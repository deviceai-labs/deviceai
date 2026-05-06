/**
 * archive_extract.c — tar.bz2 extractor for iOS/macOS.
 *
 * Uses system libbz2 for decompression and a minimal tar parser.
 * Called from Swift via dai_extract_tar_bz2().
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <bzlib.h>
#include "dai_archive.h"

/* ── Tar header (POSIX ustar) ───────────────────────────────────── */

typedef struct {
    char name[100];
    char mode[8];
    char uid[8];
    char gid[8];
    char size[12];
    char mtime[12];
    char checksum[8];
    char typeflag;
    char linkname[100];
    char magic[6];
    char version[2];
    char uname[32];
    char gname[32];
    char devmajor[8];
    char devminor[8];
    char prefix[155];
    char padding[12];
} tar_header_t;

static long octal_to_long(const char *str, int len) {
    long val = 0;
    for (int i = 0; i < len && str[i] >= '0' && str[i] <= '7'; i++) {
        val = val * 8 + (str[i] - '0');
    }
    return val;
}

static int mkdirs(const char *path) {
    char tmp[1024];
    snprintf(tmp, sizeof(tmp), "%s", path);
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            mkdir(tmp, 0755);
            *p = '/';
        }
    }
    return mkdir(tmp, 0755);
}

/* ── BZ2 decompression ──────────────────────────────────────────── */

static unsigned char *decompress_bz2(const char *input_path, size_t *out_size) {
    FILE *f = fopen(input_path, "rb");
    if (!f) return NULL;

    int bzerror;
    BZFILE *bzf = BZ2_bzReadOpen(&bzerror, f, 0, 0, NULL, 0);
    if (bzerror != BZ_OK) { fclose(f); return NULL; }

    size_t capacity = 64 * 1024 * 1024;  /* 64 MB initial */
    size_t total = 0;
    unsigned char *buf = (unsigned char *)malloc(capacity);
    if (!buf) { BZ2_bzReadClose(&bzerror, bzf); fclose(f); return NULL; }

    while (1) {
        if (total + 65536 > capacity) {
            capacity *= 2;
            unsigned char *newbuf = (unsigned char *)realloc(buf, capacity);
            if (!newbuf) { free(buf); BZ2_bzReadClose(&bzerror, bzf); fclose(f); return NULL; }
            buf = newbuf;
        }
        int nread = BZ2_bzRead(&bzerror, bzf, buf + total, 65536);
        if (bzerror == BZ_OK || bzerror == BZ_STREAM_END) {
            total += nread;
            if (bzerror == BZ_STREAM_END) break;
        } else {
            free(buf);
            BZ2_bzReadClose(&bzerror, bzf);
            fclose(f);
            return NULL;
        }
    }

    BZ2_bzReadClose(&bzerror, bzf);
    fclose(f);
    *out_size = total;
    return buf;
}

/* ── Tar extraction ─────────────────────────────────────────────── */

static int extract_tar(const unsigned char *data, size_t data_size, const char *dest_dir) {
    size_t pos = 0;
    int files_extracted = 0;

    while (pos + 512 <= data_size) {
        const tar_header_t *hdr = (const tar_header_t *)(data + pos);

        /* Check for end-of-archive (two zero blocks) */
        int all_zero = 1;
        for (int i = 0; i < 512; i++) {
            if (data[pos + i] != 0) { all_zero = 0; break; }
        }
        if (all_zero) break;

        /* Build full name (prefix + name) */
        char fullname[512];
        if (hdr->prefix[0]) {
            snprintf(fullname, sizeof(fullname), "%.*s/%.*s",
                     (int)sizeof(hdr->prefix), hdr->prefix,
                     (int)sizeof(hdr->name), hdr->name);
        } else {
            snprintf(fullname, sizeof(fullname), "%.*s",
                     (int)sizeof(hdr->name), hdr->name);
        }

        long file_size = octal_to_long(hdr->size, 12);
        char fullpath[1536];
        snprintf(fullpath, sizeof(fullpath), "%s/%s", dest_dir, fullname);

        pos += 512;  /* advance past header */

        switch (hdr->typeflag) {
            case '5':  /* directory */
            case 'D':
                mkdirs(fullpath);
                break;

            case '0':  /* regular file */
            case '\0': {
                /* Ensure parent directory exists */
                char parent[1536];
                snprintf(parent, sizeof(parent), "%s", fullpath);
                char *last_slash = strrchr(parent, '/');
                if (last_slash) {
                    *last_slash = '\0';
                    mkdirs(parent);
                }

                if (pos + file_size > data_size) return -1;

                FILE *out = fopen(fullpath, "wb");
                if (out) {
                    fwrite(data + pos, 1, file_size, out);
                    fclose(out);
                    files_extracted++;
                }
                break;
            }
            default:
                /* skip symlinks, hardlinks, etc */
                break;
        }

        /* Advance to next 512-byte boundary */
        pos += ((file_size + 511) / 512) * 512;
    }

    return files_extracted;
}

/* ── Public C API ───────────────────────────────────────────────── */

/**
 * Extract a .tar.bz2 archive to a destination directory.
 *
 * @param archive_path  Path to the .tar.bz2 file.
 * @param dest_dir      Directory to extract into (created if needed).
 * @return Number of files extracted, or -1 on error.
 */
int dai_extract_tar_bz2(const char *archive_path, const char *dest_dir) {
    if (!archive_path || !dest_dir) return -1;

    mkdirs(dest_dir);

    size_t tar_size = 0;
    unsigned char *tar_data = decompress_bz2(archive_path, &tar_size);
    if (!tar_data) return -1;

    int result = extract_tar(tar_data, tar_size, dest_dir);
    free(tar_data);
    return result;
}

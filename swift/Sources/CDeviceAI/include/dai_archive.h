#ifndef DAI_ARCHIVE_H
#define DAI_ARCHIVE_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Extract a .tar.bz2 archive to a destination directory.
 *
 * @param archive_path  Path to the .tar.bz2 file.
 * @param dest_dir      Directory to extract into (created if needed).
 * @return Number of files extracted, or -1 on error.
 */
int dai_extract_tar_bz2(const char *archive_path, const char *dest_dir);

#ifdef __cplusplus
}
#endif

#endif /* DAI_ARCHIVE_H */

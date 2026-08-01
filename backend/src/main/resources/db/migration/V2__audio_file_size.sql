-- RSS enclosure 需要文件长度
ALTER TABLE audio_versions ADD COLUMN file_size BIGINT NULL AFTER duration_ms;

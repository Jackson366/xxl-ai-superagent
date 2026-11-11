package com.xxl.ai.api.business.skill.service.impl;

import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 读取上传的技能包与元数据，保留技能目录内的 UTF-8 文件树。
 *
 * @author xxl-ai 2026-11-11
 */
final class SkillUploadParser {

    static final String MANIFEST = "SKILL.md";
    private static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CONTENT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_DEPTH = 20;
    private static final Set<String> IGNORED_NAMES = Set.of("__MACOSX", ".DS_Store", ".git", "node_modules");

    private SkillUploadParser() {
    }

    /* 文件树键为相对路径，目录的值为 null，父目录排在子节点之前 */
    record ParsedSkill(String name, String description, String version, Map<String, String> files) {
    }

    /* 单个 Markdown 文件直接作为技能入口，ZIP 可以包含多个独立的技能根目录 */
    static List<ParsedSkill> read(MultipartFile upload) throws IOException {
        if (upload.isEmpty() || upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("请选择非空文件，上传上限为 8 MB");
        }
        String filename = upload.getOriginalFilename();
        String extension = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (extension.endsWith(".md")) {
            try (InputStream input = upload.getInputStream()) {
                String manifest = readText(input, MAX_FILE_BYTES, MANIFEST);
                return List.of(parseManifest(Map.of(MANIFEST, manifest)));
            }
        }
        if (!extension.endsWith(".zip")) {
            throw new IllegalArgumentException("请选择 Markdown 文件或 ZIP 技能包");
        }

        /* 通过 ZIP 中央目录读取文件，不将归档内的路径写入服务器文件系统 */
        Path temporary = Files.createTempFile("xxl-skill-", ".zip");
        try {
            upload.transferTo(temporary);
            try (ZipFile zip = new ZipFile(temporary.toFile(), StandardCharsets.UTF_8)) {
                return readArchive(zip);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /* 先定位每个技能根目录，再读取其中的文件 */
    private static List<ParsedSkill> readArchive(ZipFile zip) throws IOException {
        if (zip.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("ZIP 技能包最多包含 512 个文件或目录");
        }
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        Set<String> pathKeys = new HashSet<>();
        Enumeration<? extends ZipEntry> iterator = zip.entries();
        while (iterator.hasMoreElements()) {
            ZipEntry entry = iterator.nextElement();
            String path = archivePath(entry);
            if (path.isEmpty()) {
                continue;
            }
            if (!pathKeys.add(path.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("ZIP 中存在重复路径：" + path);
            }
            entries.put(path, entry);
        }

        List<String> manifests = entries.keySet().stream()
                .filter(path -> !entries.get(path).isDirectory())
                .filter(path -> path.equals(MANIFEST) || path.endsWith("/" + MANIFEST))
                .sorted(Comparator.comparingInt(String::length))
                .toList();
        List<String> roots = new ArrayList<>();
        for (String manifest : manifests) {
            if (roots.stream().noneMatch(manifest::startsWith)) {
                roots.add(manifest.substring(0, manifest.length() - MANIFEST.length()));
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中未找到 SKILL.md");
        }

        List<ParsedSkill> skills = new ArrayList<>();
        long remainingBytes = MAX_CONTENT_BYTES;
        int nodeCount = 0;
        for (String root : roots) {
            Map<String, String> files = new TreeMap<>();
            for (Map.Entry<String, ZipEntry> item : entries.entrySet()) {
                String path = item.getKey();
                if (!path.startsWith(root)) {
                    continue;
                }
                String relativePath = path.substring(root.length());
                ZipEntry entry = item.getValue();
                String content = null;
                if (!entry.isDirectory()) {
                    long size = entry.getSize();
                    if (size < 0 || size > MAX_FILE_BYTES) {
                        throw new IllegalArgumentException("技能包中的单个文件上限为 2 MB：" + relativePath);
                    }
                    if (size > remainingBytes) {
                        throw new IllegalArgumentException("ZIP 中的技能文件解压后合计不能超过 16 MB");
                    }
                    try (InputStream input = zip.getInputStream(entry)) {
                        content = readText(input, (int) Math.min(size, remainingBytes), relativePath);
                    }
                    remainingBytes -= size;
                }
                files.put(relativePath, content);
            }
            ParsedSkill skill = parseManifest(files);
            nodeCount += skill.files().size();
            if (nodeCount > MAX_ENTRIES) {
                throw new IllegalArgumentException("技能目录展开后最多包含 512 个文件或目录");
            }
            skills.add(skill);
        }
        return skills;
    }

    /* 解析归档路径，允许常见压缩工具添加的 ./ 前缀 */
    private static String archivePath(ZipEntry entry) {
        String path = entry.getName();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("ZIP 中存在无效的文件或目录路径");
        }
        if (entry.isDirectory() && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty() && entry.isDirectory()) {
            return "";
        }
        String[] segments = path.split("/", -1);
        if (segments.length > MAX_DEPTH) {
            throw new IllegalArgumentException("ZIP 中的目录层级不能超过 20 层");
        }
        for (String segment : segments) {
            if (segment.isBlank() || segment.length() > 200 || !segment.equals(segment.strip())
                    || segment.equals(".") || segment.contains("..") || segment.indexOf('\\') >= 0
                    || segment.indexOf(':') >= 0 || segment.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("ZIP 中存在无效的文件或目录路径");
            }
        }
        for (String segment : segments) {
            if (IGNORED_NAMES.contains(segment)) {
                return "";
            }
        }
        return path;
    }

    /* 补齐压缩包中省略的父目录，并检查固定节点和文件/目录重名 */
    private static void completeTree(Map<String, String> files) {
        for (String path : List.copyOf(files.keySet())) {
            for (int end = path.lastIndexOf('/'); end >= 0; end = path.lastIndexOf('/', end - 1)) {
                requireDirectory(files, path.substring(0, end));
            }
        }
        requireDirectory(files, "scripts");
        requireDirectory(files, "reference");

        Set<String> pathKeys = new HashSet<>();
        for (String path : files.keySet()) {
            if (!pathKeys.add(path.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("技能目录中存在重复路径：" + path);
            }
        }
    }

    private static void requireDirectory(Map<String, String> files, String path) {
        if (files.putIfAbsent(path, null) != null) {
            throw new IllegalArgumentException("技能目录与文件重名：" + path);
        }
    }

    /* 只接收现有文件树能存储的文本，并按实际解压字节数限制读取 */
    private static String readText(InputStream input, int limit, String path) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw new IllegalArgumentException("技能文件超过大小限制：" + path);
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("技能包仅支持 UTF-8 文本文件：" + path, ex);
        }
        if (content.chars().anyMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\r' && c != '\n')) {
            throw new IllegalArgumentException("技能包仅支持 UTF-8 文本文件：" + path);
        }
        return content;
    }

    /* 读取 YAML frontmatter，保留文件正文以及声明的扩展字段 */
    private static ParsedSkill parseManifest(Map<String, String> files) {
        String content = files.get(MANIFEST);
        String[] lines = content.replaceFirst("^\uFEFF", "").split("\\R", -1);
        int end = 1;
        while (end < lines.length && !"---".equals(lines[end].strip())) {
            end++;
        }
        if (!"---".equals(lines[0].strip()) || end == lines.length) {
            throw new IllegalArgumentException("SKILL.md 需要以 YAML 区块开头，填写 name 和 description");
        }

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(64 * 1024);
        Object metadata;
        try {
            String header = String.join("\n", Arrays.copyOfRange(lines, 1, end));
            metadata = new Yaml(new SafeConstructor(options)).load(header);
        } catch (YAMLException ex) {
            throw new IllegalArgumentException("SKILL.md 的 YAML 格式有误", ex);
        }
        if (!(metadata instanceof Map<?, ?> fields) || !(fields.get("name") instanceof String name)
                || !(fields.get("description") instanceof String description)
                || name.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md 需要填写有效的 name 和 description");
        }
        String version = fields.get("version") == null ? "" : String.valueOf(fields.get("version")).strip();
        if (description.strip().length() > 500 || version.length() > 20) {
            throw new IllegalArgumentException("技能描述最多 500 字，版本号最多 20 字");
        }
        Map<String, String> normalizedFiles = new TreeMap<>(files);
        normalizedFiles.put(MANIFEST, content.replaceFirst("^\uFEFF", ""));
        completeTree(normalizedFiles);
        return new ParsedSkill(name.strip(), description.strip(), version.isEmpty() ? "1.0" : version, normalizedFiles);
    }
}

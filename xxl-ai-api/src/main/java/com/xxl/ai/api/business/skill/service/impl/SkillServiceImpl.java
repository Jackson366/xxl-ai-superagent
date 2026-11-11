package com.xxl.ai.api.business.skill.service.impl;

import com.xxl.ai.api.business.skill.mapper.SkillFileMapper;
import com.xxl.ai.api.business.skill.mapper.SkillMapper;
import com.xxl.ai.api.business.skill.model.adaptor.SkillAdaptor;
import com.xxl.ai.api.business.skill.model.dto.SkillDTO;
import com.xxl.ai.api.business.skill.model.entity.Skill;
import com.xxl.ai.api.business.skill.model.entity.SkillFile;
import com.xxl.ai.api.business.skill.service.SkillService;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SKILL Service 实现
 *
 * @author xxl-ai 2026-09-06
 */
@Service
public class SkillServiceImpl implements SkillService {

    /** 根级固定文件：SKILL.md 文件名 */
    private static final String FILE_NAME_MAIN = SkillUploadParser.MANIFEST;
    /** 根级固定目录：scripts、reference（社区约定骨架） */
    private static final String DIR_NAME_SCRIPTS = "scripts";
    private static final String DIR_NAME_REFERENCE = "reference";

    @Resource
    private SkillMapper skillMapper;
    @Resource
    private SkillFileMapper skillFileMapper;

    /**
     * 分页查询 SKILL 列表
     */
    @Override
    public PageModel<SkillDTO> pageList(long spaceId, int offset, int pagesize, String name, int status) {
        List<Skill> pageList = skillMapper.pageList(spaceId, offset, pagesize, name, status);
        int totalCount = skillMapper.pageListCount(spaceId, offset, pagesize, name, status);
        List<SkillDTO> pageListDto = SkillAdaptor.adapt2dto(pageList);
        PageModel<SkillDTO> pageModel = new PageModel<>();
        pageModel.setData(pageListDto);
        pageModel.setTotal(totalCount);
        return pageModel;
    }

    /**
     * 新增 SKILL：校验名称唯一后落库，并播种『固定文件』：
     * SKILL.md（根级文件，锁定）+ scripts/、reference/（根级目录，锁定）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> insert(long spaceId, SkillDTO dto) {
        Skill skill = SkillAdaptor.adapt(dto);
        if (skill == null || StringTool.isBlank(skill.getName())) {
            return Response.ofFail("SKILL名称不能为空");
        }
        String name = skill.getName().trim();
        skill.setName(name);
        if (!isValidName(name)) {
            return Response.ofFail("SKILL名称仅支持字母/数字/中划线，不能包含路径分隔符");
        }
        if (skillMapper.countByName(spaceId, name, 0) > 0) {
            return Response.ofFail("SKILL名称[" + name + "]在当前空间已存在");
        }
        if (StringTool.isBlank(skill.getVersion())) {
            skill.setVersion("1.0");
        }
        skill.setSpaceId(spaceId);
        skillMapper.insert(skill);
        // 播种固定文件：SKILL.md + scripts/ + reference/
        insertSeedFiles(skill.getId(), skill);
        return Response.ofSuccess();
    }

    /**
     * 批量导入技能。全部校验完成后再写入，文件树写入失败时整包回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<List<SkillDTO>> upload(long spaceId, MultipartFile file) {
        List<SkillUploadParser.ParsedSkill> skills;
        try {
            skills = SkillUploadParser.read(file);
        } catch (IllegalArgumentException ex) {
            return Response.ofFail(ex.getMessage());
        } catch (IOException ex) {
            return Response.ofFail("无法读取技能文件，请检查文件编码或 ZIP 是否完整");
        }

        Set<String> names = new HashSet<>();
        for (SkillUploadParser.ParsedSkill skill : skills) {
            String name = skill.name();
            if (!isValidName(name)) {
                return Response.ofFail("SKILL名称仅支持字母/数字/中划线，长度不超过 100：" + name);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                return Response.ofFail("技能包中存在同名 SKILL[" + name + "]");
            }
            if (skillMapper.countByName(spaceId, name, 0) > 0) {
                return Response.ofFail("SKILL名称[" + name + "]在当前空间已存在");
            }
        }

        List<SkillDTO> imported = new ArrayList<>();
        for (SkillUploadParser.ParsedSkill parsed : skills) {
            SkillDTO dto = new SkillDTO();
            dto.setSpaceId(spaceId);
            dto.setName(parsed.name());
            dto.setDescription(parsed.description());
            dto.setVersion(parsed.version());
            dto.setStatus(0);
            Skill skill = SkillAdaptor.adapt(dto);
            skillMapper.insert(skill);
            insertUploadedFiles(skill.getId(), parsed.files());
            dto.setId(skill.getId());
            imported.add(dto);
        }
        return Response.ofSuccess(imported);
    }

    /**
     * 批量删除 SKILL（级联删除内容文件）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> deleteByIds(List<Long> ids) {
        if (CollectionTool.isEmpty(ids)) {
            return Response.ofFail("请选择要删除的 SKILL");
        }
        int ret = skillMapper.deleteByIds(ids);
        skillFileMapper.deleteBySkillIds(ids);
        return ret > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    /**
     * 更新 SKILL
     */
    @Override
    public Response<String> update(SkillDTO dto) {
        Skill skill = SkillAdaptor.adapt(dto);
        if (skill == null || StringTool.isBlank(skill.getName())) {
            return Response.ofFail("SKILL名称不能为空");
        }
        String name = skill.getName().trim();
        skill.setName(name);
        if (!isValidName(name)) {
            return Response.ofFail("SKILL名称仅支持字母/数字/中划线，不能包含路径分隔符");
        }
        Skill exist = skillMapper.load(skill.getId());
        if (exist == null) {
            return Response.ofFail("SKILL不存在");
        }
        if (skillMapper.countByName(exist.getSpaceId(), name, exist.getId()) > 0) {
            return Response.ofFail("SKILL名称[" + name + "]在当前空间已存在");
        }
        skill.setSpaceId(exist.getSpaceId());
        if (StringTool.isBlank(skill.getVersion())) {
            skill.setVersion("1.0");
        }
        int ret = skillMapper.update(skill);
        return ret > 0 ? Response.ofSuccess() : Response.ofFail();
    }

    /**
     * 查询空间内 SKILL 列表
     */
    @Override
    public List<Skill> listBySpace(long spaceId) {
        return skillMapper.listBySpace(spaceId);
    }

    /**
     * 按ID集合查询 SKILL 列表
     */
    @Override
    public List<Skill> listByIds(List<Long> ids) {
        return skillMapper.listByIds(ids);
    }

    /**
     * 名称格式校验：字母/数字/中划线，长度≤100
     */
    private boolean isValidName(String name) {
        if (name.length() > 100) {
            return false;
        }
        for (char c : name.toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按父节点优先的顺序写入上传文件树，沿用现有的固定节点规则。
     */
    private void insertUploadedFiles(long skillId, Map<String, String> files) {
        Map<String, Long> directoryIds = new HashMap<>();
        directoryIds.put("", 0L);
        int sort = 4;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            int separator = path.lastIndexOf('/');
            String name = path.substring(separator + 1);
            String parent = separator < 0 ? "" : path.substring(0, separator);
            String content = entry.getValue();
            boolean directory = content == null;
            int fixedSort = switch (path) {
                case FILE_NAME_MAIN -> 1;
                case DIR_NAME_SCRIPTS -> 2;
                case DIR_NAME_REFERENCE -> 3;
                default -> 0;
            };

            SkillFile node = new SkillFile();
            node.setSkillId(skillId);
            node.setParentId(directoryIds.get(parent));
            node.setName(name);
            node.setType(directory ? 0 : 1);
            node.setContent(content);
            node.setLocked(fixedSort == 0 ? 0 : 1);
            node.setSort(fixedSort == 0 ? sort++ : fixedSort);
            if (!directory) {
                String type = SkillFileServiceImpl.parseFileType(name);
                node.setFileType(type.length() > 20 ? "txt" : type);
            }
            skillFileMapper.insert(node);
            if (directory) {
                directoryIds.put(path, node.getId());
            }
        }
    }

    /**
     * 播种根级固定文件：SKILL.md（含 frontmatter）+ scripts/、reference/（锁定目录）
     */
    private void insertSeedFiles(long skillId, Skill skill) {
        // SKILL.md：根级文件，锁定，默认内容含 frontmatter（名称/描述）
        SkillFile mainFile = new SkillFile();
        mainFile.setSkillId(skillId);
        mainFile.setParentId(0);
        mainFile.setName(FILE_NAME_MAIN);
        mainFile.setType(1);
        mainFile.setFileType("md");
        mainFile.setLocked(1);
        mainFile.setSort(1);
        mainFile.setContent(buildMainContent(skill));
        skillFileMapper.insert(mainFile);

        // scripts/：根级目录，锁定，可自由新增脚本
        insertSeedDir(skillId, DIR_NAME_SCRIPTS, 2);
        // reference/：根级目录，锁定，可自由新增参考文档
        insertSeedDir(skillId, DIR_NAME_REFERENCE, 3);
    }

    /**
     * SKILL.md 默认内容：frontmatter（name/description）+ 引导文案
     */
    private String buildMainContent(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skill.getName()).append("\n");
        sb.append("description: ").append(skill.getDescription() == null ? "" : skill.getDescription()).append("\n");
        sb.append("---\n\n");
        sb.append("# ").append(skill.getName()).append("\n\n");
        sb.append("本 Skill 文件树符合社区规范：`SKILL.md` 为入口，引用文件用相对路径。\n\n");
        sb.append("- `scripts/`：可存放可执行脚本（.py / .sh / .js 等）\n");
        sb.append("- `reference/`：可存放参考文档（.md / .json / .yaml 等）\n");
        sb.append("- 其他文件/目录可自由新增扩展\n");
        return sb.toString();
    }

    /**
     * 播种根级固定目录（锁定，可扩展内容）
     */
    private void insertSeedDir(long skillId, String name, int sort) {
        SkillFile dir = new SkillFile();
        dir.setSkillId(skillId);
        dir.setParentId(0);
        dir.setName(name);
        dir.setType(0);
        dir.setLocked(1);
        dir.setSort(sort);
        skillFileMapper.insert(dir);
    }

}

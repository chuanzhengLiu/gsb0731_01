package com.podcast.web.dto;

import java.util.List;

public class StructureTemplateDtos {

    /** A single fixed section of a show's structure (README §4.1 节目结构模板). */
    public record TemplateSection(
            String name,
            Long targetDurationMs
    ) {}

    /** The template stored on a podcast as structure_template_json. */
    public record StructureTemplate(
            List<TemplateSection> sections
    ) {}

    /** Result of comparing an episode's actual duration against the template. */
    public record StructureComparison(
            Long episodeId,
            boolean hasTemplate,
            Long templateTotalMs,
            Long actualDurationMs,
            Long diffMs,               // actual - template (null when unknown)
            List<TemplateSection> sections
    ) {}
}

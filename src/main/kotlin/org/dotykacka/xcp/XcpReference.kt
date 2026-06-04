package org.dotykacka.xcp

object XcpReference {
    val KEYWORDS = setOf("true", "false", "null")

    val TOP_LEVEL_KEYS = listOf(
        "id", "version", "name", "title", "description", "description_extended", "deploy_phase", "start_phase",
        "detail_view", "table_view", "input", "structs", "variables", "functions", "phases", "operations",
        "tags", "document_types", "evrecord_types", "diary_types"
    )

    val COMMON_KEYS = listOf(
        "id", "name", "type", "default_value", "select", "allow_custom_value", "doctype", "tag_category",
        "user_states", "hint", "parameter", "input", "required", "display", "show_in_tables", "display_column",
        "system_name", "tags", "properties", "steps", "actions", "outputs", "condition", "next_phase",
        "subject", "body", "recipient", "variables", "value", "fields", "width", "readonly", "hidden"
    )

    val DYNAMIC_FIELD_KEYS = listOf("var", "tpl", "js", "out")

    val VARIABLE_TYPES = listOf(
        "any", "boolean", "contact", "date", "datetime", "diary", "document", "email", "evrecord", "file",
        "html", "keyword", "long", "multiline-text", "number", "process", "tag", "text", "time", "user",
        "userset"
    )

    val ACTION_NAMES = listOf(
        "assignNumber", "contactCreate", "contactEdit", "contactFindByEmail", "contactFindById",
        "contactFindByOrganization", "diaryCreate", "documentCreate", "documentDelete", "documentFind",
        "documentLock", "emailSend", "eventCreate", "eventDelete", "eventEdit", "messageSend",
        "processReadVariables", "processSchedule", "processStart", "projectCreate", "projectEdit",
        "requestApproval", "setVariables", "userFindManagers"
    )
}

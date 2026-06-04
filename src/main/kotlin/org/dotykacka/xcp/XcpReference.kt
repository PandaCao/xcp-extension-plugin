package org.dotykacka.xcp

object XcpReference {
    val KEYWORDS = setOf("true", "false", "null")

    val TOP_LEVEL_KEYS = listOf(
        "id", "version", "name", "title", "description", "description_extended", "deploy_phase", "start_phase",
        "detail_view", "table_view", "input", "structs", "variables", "functions", "phases", "operations",
        "tags", "document_types", "evrecord_types", "diary_types"
    )

    val COMMON_KEYS = listOf(
        "access", "acl", "action", "actions", "actor", "agendas", "allday", "allow_create",
        "allow_custom_value", "all", "amount", "assignee", "attachments", "attendance", "attendants",
        "auto_outputs", "bcc", "bic", "body", "bosses", "calendar", "calendar_name", "calendar_owner",
        "calendar_type", "cancel_step", "cc", "changed", "client_contact", "clone", "closed", "code",
        "color", "comment", "condition", "constant_symbol", "contact", "context", "cont", "created",
        "created_by", "create_dt", "creator", "criteria", "currency", "date", "date_from", "date_to",
        "deadline", "default", "def_id", "def_version", "description", "display", "display_column", "display_name",
        "doctype", "document", "documents", "due_date", "email", "enabled", "end", "eq",
        "error_message", "errors_handled", "event", "event_id", "evrecords", "extras", "fields",
        "folder_path", "folders", "format", "form", "from", "full_def_id", "ge", "hidden", "hint",
        "holder", "html", "html_template", "iban", "icon", "include_date", "index", "in", "input",
        "inputs", "items", "label", "last_changed", "last_changed_by", "le", "linkuri", "links",
        "location", "lock", "login", "manager", "manager_post_id", "manager_post_name",
        "manager_post_user", "max_size", "max_value", "message", "mime_type", "mode", "modify_dt",
        "name", "ncont", "ne", "next_phase", "next_run", "ni", "object", "object_type", "options",
        "organizer", "outputs", "owner", "parameter", "parameters", "parent_system_name", "phase",
        "phase_id", "post_name_regex", "postponed_start", "process", "process_id", "process_state",
        "processes", "project", "project_id", "properties", "property", "readonly", "recipient",
        "regex_post_id", "regex_post_name", "regex_post_user", "regex_post_users", "reminder",
        "reply_to", "required", "requiredFields", "resolved", "result", "right", "role",
        "rr_days", "rr_interval", "scheduled", "select", "sequence", "sequenceNext", "show_in_tables",
        "specific_symbol", "spayd", "start", "state", "steps", "subject", "submitter", "subfolders",
        "success", "suggestions", "system_name", "table", "tag_category", "tags", "target", "task",
        "tasks", "team", "telephone", "template", "text", "text_template", "time_budget",
        "time_estimated", "timeout", "title", "to", "transitions", "type", "type_name",
        "type_property", "unit_id", "unit_name", "unit_name_regex", "url", "user", "user_editable",
        "user_post_type", "user_states", "value", "variable", "variable_symbol", "variables",
        "version", "wake", "width"
    )

    val DYNAMIC_FIELD_KEYS = listOf("var", "tpl", "js", "out")

    val FIELD_KEYS = (TOP_LEVEL_KEYS + COMMON_KEYS + DYNAMIC_FIELD_KEYS).toSet()

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

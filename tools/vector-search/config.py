from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DB_PATH = REPO_ROOT / ".vector-db"
MANIFEST_PATH = DB_PATH / "manifest.json"
TABLE_NAME = "farmflow_code"
DEFAULT_MODEL_NAME = "all-MiniLM-L6-v2"
BATCH_SIZE = 32

IGNORED_DIRS = {
    "target",
    "bin",
    ".vs",
    ".vscode",
    ".idea",
    ".git",
    "testresults",
    "coverage",
    "node_modules",
    "dist",
    ".vector-db",
    "tools",
    "scratch",
    "__pycache__",
    ".pytest_cache",
    ".venv",
    "venv",
}

IGNORED_FILE_PATTERNS = [
    ".env",
    ".env.*",
    "*.key",
    "*.pem",
    "*.pfx",
    "*.p12",
    "*.cert",
    "*.jar",
    "*.class",
    "*.war",
    "*.user",
    "*.DS_Store",
    "thumbs.db",
]

ALLOWED_EXTENSIONS = {
    ".java",
    ".xml",
    ".sql",
    ".json",
    ".yaml",
    ".yml",
    ".md",
    ".js",
    ".ps1",
    ".sh",
}

SPECIAL_ALLOWED_FILES = {
    "dockerfile",
    ".dockerignore",
    ".editorconfig",
    ".gitattributes",
}


def is_file_eligible(rel_path_str: str) -> bool:
    normalized = rel_path_str.replace("\\", "/").strip().lower()
    parts = normalized.split("/")

    for part in parts[:-1]:
        if part in IGNORED_DIRS:
            return False

    filename = parts[-1]

    if filename.startswith(".env"):
        return False

    for pat in IGNORED_FILE_PATTERNS:
        if pat.startswith("*") and filename.endswith(pat[1:]):
            return False
        if pat == filename:
            return False

    suffix = Path(filename).suffix.lower()
    if suffix in ALLOWED_EXTENSIONS or filename in SPECIAL_ALLOWED_FILES:
        return True

    return False


def get_project_area(rel_path_str: str) -> str:
    path_lower = rel_path_str.replace("\\", "/").strip().lower()
    if "src/main/java/com/farmflow/entity" in path_lower:
        return "Entity"
    if "src/main/java/com/farmflow/service" in path_lower:
        return "Service"
    if "src/main/java/com/farmflow/repository" in path_lower:
        return "Repository"
    if "src/main/java/com/farmflow/controller" in path_lower:
        return "Controller"
    if "src/main/java/com/farmflow/config" in path_lower:
        return "Config"
    if "src/main/java/com/farmflow/consumer" in path_lower:
        return "Consumer"
    if path_lower.startswith("src/test/"):
        return "Tests"
    if path_lower.startswith("docs/"):
        return "Documentation"
    if path_lower.startswith("load-tests/"):
        return "LoadTests"
    if path_lower.startswith("keycloak/"):
        return "Keycloak"
    if path_lower.endswith(".md"):
        return "Documentation"
    return "Configuration"


def escape_sql_literal(val: str) -> str:
    return val.replace("\\", "/").replace("'", "''")
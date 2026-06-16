#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const ROOT = process.cwd();
const REQUIRED_DIRS = [
  "raw",
  "raw/notes",
  "wiki",
  "wiki/summaries",
  "wiki/baselines",
  "archive",
];

const RAW_NOTE_RE = /^[^-\\\/]+-\d{8}-\d{4}-需求记录\.md$/u;
const SUMMARY_FILE_RE = /^[^\\\/]+-v\d+(?:\.\d+)*\.md$/u;
const BASELINE_DIR_RE = /^BL-\d{8}-\d{2}$/u;
const IGNORED_DIRS = new Set([".git", "node_modules", ".venv", "__pycache__", ".pytest_cache"]);

const issues = [];

function rel(filePath) {
  return path.relative(ROOT, filePath).replace(/\\/g, "/") || ".";
}

function addIssue(type, filePath, message) {
  issues.push(`[${type}] ${rel(filePath)}: ${message}`);
}

function exists(relPath) {
  return fs.existsSync(path.join(ROOT, relPath));
}

function walk(dir) {
  if (!fs.existsSync(dir)) return [];

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (IGNORED_DIRS.has(entry.name)) continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(fullPath));
    } else {
      files.push(fullPath);
    }
  }

  return files;
}

function readText(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function stripMdExtension(name) {
  return name.replace(/\.md$/iu, "");
}

function buildMarkdownIndex(markdownFiles) {
  const index = new Map();

  for (const file of markdownFiles) {
    const relPath = rel(file);
    const noExt = stripMdExtension(relPath);
    const base = stripMdExtension(path.basename(file));

    for (const key of [relPath, noExt, base]) {
      if (!index.has(key)) index.set(key, []);
      index.get(key).push(file);
    }
  }

  return index;
}

function normalizeWikiTarget(target) {
  return target
    .split("|")[0]
    .split("#")[0]
    .trim()
    .replace(/\\/g, "/");
}

function checkStructure() {
  for (const dir of REQUIRED_DIRS) {
    if (!exists(dir)) addIssue("结构完整性", path.join(ROOT, dir), "缺少必需目录");
  }
}

function checkNaming() {
  const rawDir = path.join(ROOT, "raw", "notes");
  if (fs.existsSync(rawDir)) {
    for (const file of fs.readdirSync(rawDir, { withFileTypes: true })) {
      if (file.isFile() && file.name.endsWith(".md") && !RAW_NOTE_RE.test(file.name)) {
        addIssue(
          "结构完整性",
          path.join(rawDir, file.name),
          "raw/notes 文件命名应为 {涉众角色}-{YYYYMMDD-HHMM}-需求记录.md",
        );
      }
    }
  }

  const summariesDir = path.join(ROOT, "wiki", "summaries");
  if (fs.existsSync(summariesDir)) {
    for (const file of fs.readdirSync(summariesDir, { withFileTypes: true })) {
      if (
        file.isFile() &&
        file.name.endsWith(".md") &&
        !file.name.startsWith("00-") &&
        !SUMMARY_FILE_RE.test(file.name)
      ) {
        addIssue(
          "结构完整性",
          path.join(summariesDir, file.name),
          "wiki/summaries 文件命名应为 {文档类型}-v{版本号}.md",
        );
      }
    }
  }

  const baselinesDir = path.join(ROOT, "wiki", "baselines");
  if (fs.existsSync(baselinesDir)) {
    for (const entry of fs.readdirSync(baselinesDir, { withFileTypes: true })) {
      if (entry.isDirectory() && !BASELINE_DIR_RE.test(entry.name)) {
        addIssue(
          "结构完整性",
          path.join(baselinesDir, entry.name),
          "wiki/baselines 目录命名应为 BL-YYYYMMDD-NN",
        );
      }
    }
  }
}

function checkLinks(markdownFiles) {
  const index = buildMarkdownIndex(markdownFiles);
  const linkRe = /\[\[([^\]]+)\]\]/gu;

  for (const file of markdownFiles) {
    const lines = readText(file).split(/\r?\n/u);
    let fence = null;

    for (const line of lines) {
      const fenceMatch = line.match(/^\s*(```|~~~)/u);
      if (fenceMatch) {
        if (!fence) fence = fenceMatch[1];
        else if (fence === fenceMatch[1]) fence = null;
        continue;
      }

      if (fence) continue;

      for (const match of line.matchAll(linkRe)) {
        const target = normalizeWikiTarget(match[1]);
        if (!target) continue;

        const candidates = [];
        candidates.push(target);
        if (!target.endsWith(".md")) candidates.push(`${target}.md`);

        const resolved = candidates.some((candidate) => {
          const directPath = path.join(ROOT, candidate);
          return fs.existsSync(directPath) || index.has(stripMdExtension(candidate));
        });

        if (!resolved) {
          addIssue("链接有效性", file, `双向链接 [[${match[1]}]] 指向的目标文件不存在`);
        }
      }
    }
  }
}

function splitMarkdownTableRow(line) {
  const trimmed = line.trim();
  if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return null;

  const cells = [];
  let current = "";
  let escaped = false;

  for (let i = 1; i < trimmed.length - 1; i += 1) {
    const char = trimmed[i];
    if (char === "\\" && !escaped) {
      escaped = true;
      current += char;
      continue;
    }
    if (char === "|" && !escaped) {
      cells.push(current.trim());
      current = "";
      continue;
    }
    current += char;
    escaped = false;
  }
  cells.push(current.trim());

  return cells;
}

function isSeparatorRow(cells) {
  return cells.every((cell) => /^:?-{3,}:?$/u.test(cell.trim()));
}

function checkMarkdownFormat(markdownFiles) {
  for (const file of markdownFiles) {
    const text = readText(file);
    const lines = text.split(/\r?\n/u);
    let fence = null;
    let tableExpectedColumns = null;
    let tableStartLine = null;

    lines.forEach((line, index) => {
      const fenceMatch = line.match(/^\s*(```|~~~)/u);
      if (fenceMatch) {
        if (!fence) fence = { marker: fenceMatch[1], line: index + 1 };
        else if (fence.marker === fenceMatch[1]) fence = null;
      }

      const cells = splitMarkdownTableRow(line);
      if (!cells) {
        tableExpectedColumns = null;
        tableStartLine = null;
        return;
      }

      if (tableExpectedColumns === null) {
        tableExpectedColumns = cells.length;
        tableStartLine = index + 1;
        return;
      }

      if (cells.length !== tableExpectedColumns) {
        addIssue(
          "内容格式",
          file,
          `Markdown 表格第 ${index + 1} 行列数为 ${cells.length}，应为 ${tableExpectedColumns}（表格起始行 ${tableStartLine}）`,
        );
      }

      if (index === tableStartLine && !isSeparatorRow(cells)) {
        addIssue("内容格式", file, `Markdown 表格第 ${index + 1} 行不是合法分隔行`);
      }
    });

    if (fence) {
      addIssue("内容格式", file, `代码块未闭合，起始行 ${fence.line}`);
    }
  }
}

function checkBaselineConsistency() {
  const baselinesDir = path.join(ROOT, "wiki", "baselines");
  if (!fs.existsSync(baselinesDir)) return;

  const requiredFiles = [
    "基线说明.md",
    "SRS-正式版.md",
    "需求清单.md",
    "需求溯源矩阵-RTM.md",
    "基线创立确认.md",
  ];

  const baselineDirs = fs
    .readdirSync(baselinesDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && BASELINE_DIR_RE.test(entry.name))
    .map((entry) => path.join(baselinesDir, entry.name));

  for (const baselineDir of baselineDirs) {
    for (const name of requiredFiles) {
      const filePath = path.join(baselineDir, name);
      if (!fs.existsSync(filePath)) {
        addIssue("基线完整性", filePath, "基线目录缺少必要文件");
      }
    }

    const umlDir = path.join(baselineDir, "UML模型");
    if (!fs.existsSync(umlDir)) {
      addIssue("基线完整性", umlDir, "基线目录缺少 UML模型 子目录");
      continue;
    }

    const umlFiles = fs.readdirSync(umlDir, { withFileTypes: true }).filter((entry) => entry.isFile());
    const hasUseCase = umlFiles.some((entry) => entry.name.startsWith("用例图") && entry.name.endsWith(".puml"));
    const hasActivity = umlFiles.some((entry) => entry.name.startsWith("活动图-") && entry.name.endsWith(".puml"));
    if (!hasUseCase) {
      addIssue("基线完整性", umlDir, "基线 UML模型 子目录缺少用例图 .puml");
    }
    if (!hasActivity) {
      addIssue("基线完整性", umlDir, "基线 UML模型 子目录缺少活动图 .puml");
    }
  }
}

function main() {
  checkStructure();
  checkNaming();

  const markdownFiles = walk(ROOT).filter((file) => file.endsWith(".md"));
  checkLinks(markdownFiles);
  checkMarkdownFormat(markdownFiles);
  checkBaselineConsistency();

  if (issues.length === 0) {
    console.log("✅ compile.js 编译通过，知识库结构正常");
    return;
  }

  for (const issue of issues) {
    console.log(issue);
  }
  process.exitCode = 1;
}

main();

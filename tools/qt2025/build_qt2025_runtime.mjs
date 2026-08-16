#!/usr/bin/env node

import { createHash } from "node:crypto";
import {
    closeSync,
    copyFileSync,
    existsSync,
    ftruncateSync,
    mkdirSync,
    openSync,
    readFileSync,
    statSync,
    writeFileSync,
    writeSync,
} from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const sourceRoot = process.argv[2] && resolve(process.argv[2]);
if (!sourceRoot) {
    throw new Error("Usage: node build_qt2025_runtime.mjs <QT2025 folder> [asset folder]");
}
const assetDir = resolve(
    process.argv[3] ?? resolve(SCRIPT_DIR, "../../app/src/main/assets/offline/qt2025"),
);
mkdirSync(assetDir, { recursive: true });

const SOURCE_FILES = [
    { path: "Names.txt", lane: "name", type: 1, mode: "insert" },
    { path: "Names2/123.txt", lane: "name_additions", type: 1, mode: "replace" },
    { path: "VietPhrase/VietPhrase.txt", lane: "vietphrase", type: 0, mode: "insert" },
    { path: "Resources/Pronouns.txt", lane: "pronoun", type: 2, mode: "insert" },
];
const RUNTIME_FILES = [
    ["Resources/ChinesePhienAmWords.txt", "ChinesePhienAmWords.txt"],
    ["Resources/HoNguoi.txt", "HoNguoi.txt"],
    ["Resources/HauTu.txt", "HauTu.txt"],
    ["LuatNhan.txt", "LuatNhan.txt"],
];

const MAGIC = Buffer.from("QTDCT001", "ascii");
const FORMAT_VERSION = 1;
const HEADER_SIZE = 32;
const MAX_LOAD_FACTOR = 0.72;
const FNV_OFFSET_BASIS = 0x811c9dc5;
const FNV_PRIME = 0x01000193;

function sha256(bytes) {
    return createHash("sha256").update(bytes).digest("hex");
}

function fnv1aUtf16(value) {
    let hash = FNV_OFFSET_BASIS;
    for (let index = 0; index < value.length; index += 1) {
        hash = Math.imul(hash ^ value.charCodeAt(index), FNV_PRIME) >>> 0;
    }
    return hash;
}

function parseDictionary(sourceFile) {
    const filePath = resolve(sourceRoot, sourceFile.path);
    if (!existsSync(filePath)) throw new Error(`Missing QT2025 input: ${filePath}`);
    const bytes = readFileSync(filePath);
    const content = bytes.toString("utf8").replace(/^\uFEFF/, "");
    const parsed = [];
    let rejected = 0;
    for (const rawLine of content.split(/\r?\n/)) {
        const line = rawLine.trimEnd();
        if (!line || line.trimStart().startsWith("#")) continue;
        const delimiter = line.indexOf("=");
        if (delimiter <= 0) {
            rejected += 1;
            continue;
        }
        const source = line.slice(0, delimiter).trim().toLowerCase();
        const target = line.slice(delimiter + 1).split(/[/|]/, 1)[0].trim();
        if (!source || !target || source.length > 0xffff) {
            rejected += 1;
            continue;
        }
        parsed.push({ source, target, type: sourceFile.type });
    }
    return { parsed, rejected, bytes: bytes.length, sha256: sha256(bytes) };
}

const entries = new Map();
const sources = [];
let rejectedLines = 0;
for (const sourceFile of SOURCE_FILES) {
    const result = parseDictionary(sourceFile);
    rejectedLines += result.rejected;
    let accepted = 0;
    let replaced = 0;
    for (const entry of result.parsed) {
        if (entries.has(entry.source)) {
            if (sourceFile.mode !== "replace") continue;
            entries.set(entry.source, entry);
            replaced += 1;
        } else {
            entries.set(entry.source, entry);
            accepted += 1;
        }
    }
    sources.push({
        name: sourceFile.path,
        lane: sourceFile.lane,
        bytes: result.bytes,
        sha256: result.sha256,
        parsedEntries: result.parsed.length,
        acceptedEntries: accepted,
        replacedEntries: replaced,
        rejectedLines: result.rejected,
    });
}

let bucketCount = 1;
while (entries.size / bucketCount > MAX_LOAD_FACTOR) bucketCount *= 2;
const bucketBytes = bucketCount * 4;
const blobOffset = HEADER_SIZE + bucketBytes;
const buckets = Buffer.alloc(bucketBytes);
const encodedEntries = [];
let cursor = blobOffset;
let maxSourceChars = 0;
let collisions = 0;

for (const entryData of entries.values()) {
    const { source, target, type } = entryData;
    const sourceBytes = Buffer.from(source, "utf16le");
    const targetBytes = Buffer.from(target, "utf8");
    const hash = fnv1aUtf16(source);
    const entry = Buffer.allocUnsafe(12 + sourceBytes.length + targetBytes.length);
    entry.writeUInt32LE(hash, 0);
    entry.writeUInt16LE(source.length, 4);
    entry.writeUInt16LE(type, 6);
    entry.writeUInt32LE(targetBytes.length, 8);
    sourceBytes.copy(entry, 12);
    targetBytes.copy(entry, 12 + sourceBytes.length);

    let bucket = hash & (bucketCount - 1);
    while (buckets.readUInt32LE(bucket * 4) !== 0) {
        collisions += 1;
        bucket = (bucket + 1) & (bucketCount - 1);
    }
    buckets.writeUInt32LE(cursor, bucket * 4);
    encodedEntries.push(entry);
    cursor += entry.length;
    maxSourceChars = Math.max(maxSourceChars, source.length);
}

const header = Buffer.alloc(HEADER_SIZE);
MAGIC.copy(header, 0);
header.writeUInt32LE(FORMAT_VERSION, 8);
header.writeUInt32LE(bucketCount, 12);
header.writeUInt32LE(entries.size, 16);
header.writeUInt32LE(maxSourceChars, 20);
header.writeUInt32LE(blobOffset, 24);
const outputPath = resolve(assetDir, "qt2025-terms.qtdict");
const file = openSync(outputPath, "w");
try {
    ftruncateSync(file, cursor);
    writeSync(file, header, 0, header.length, 0);
    writeSync(file, buckets, 0, buckets.length, HEADER_SIZE);
    let position = blobOffset;
    for (const entry of encodedEntries) {
        writeSync(file, entry, 0, entry.length, position);
        position += entry.length;
    }
} finally {
    closeSync(file);
}

const runtimeFiles = RUNTIME_FILES.map(([sourceName, outputName]) => {
    const inputPath = resolve(sourceRoot, sourceName);
    const bytes = readFileSync(inputPath);
    copyFileSync(inputPath, resolve(assetDir, outputName));
    return { name: outputName, bytes: bytes.length, sha256: sha256(bytes) };
});
const indexBytes = readFileSync(outputPath);
const engineBytes = readFileSync(resolve(sourceRoot, "TranslatorEngine.dll"));
const manifest = {
    id: "quick-translator-2025-runtime",
    formatVersion: FORMAT_VERSION,
    engineSha256: sha256(engineBytes),
    entryCount: entries.size,
    maxSourceChars,
    bucketCount,
    loadFactor: Number((entries.size / bucketCount).toFixed(6)),
    collisions,
    rejectedLines,
    index: {
        file: "qt2025-terms.qtdict",
        bytes: statSync(outputPath).size,
        sha256: sha256(indexBytes),
    },
    runtimeFiles,
    sources,
};
writeFileSync(
    resolve(assetDir, "qt2025-runtime-manifest.json"),
    `${JSON.stringify(manifest, null, 2)}\n`,
    "utf8",
);

console.log(JSON.stringify({ outputPath, ...manifest.index, entryCount: entries.size }, null, 2));

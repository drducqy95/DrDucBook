#!/usr/bin/env node

import { createHash } from "node:crypto";
import {
    closeSync,
    existsSync,
    ftruncateSync,
    openSync,
    readFileSync,
    statSync,
    writeFileSync,
    writeSync,
} from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const DEFAULT_ASSET_DIR = resolve(
    SCRIPT_DIR,
    "../../app/src/debug/assets/offline/qt2020",
);
const assetDir = resolve(process.argv[2] ?? DEFAULT_ASSET_DIR);
const outputPath = resolve(
    process.argv[3] ?? resolve(assetDir, "qt2020-terms.qtdict"),
);
const manifestPath = resolve(assetDir, "qt2020-index-manifest.json");

const SOURCE_FILES = [
    { name: "Names2.txt", lane: "name_additions" },
    { name: "Names.txt", lane: "name" },
    { name: "VietPhrase2.txt", lane: "vietphrase_additions" },
    { name: "VietPhrase.txt", lane: "vietphrase" },
    { name: "Pronouns.txt", lane: "pronoun" },
];

const MAGIC = Buffer.from("QTDCT001", "ascii");
const FORMAT_VERSION = 1;
const HEADER_SIZE = 32;
const MAX_LOAD_FACTOR = 0.72;
const FNV_OFFSET_BASIS = 0x811c9dc5;
const FNV_PRIME = 0x01000193;

function fnv1aUtf16(value) {
    let hash = FNV_OFFSET_BASIS;
    for (let index = 0; index < value.length; index += 1) {
        hash = Math.imul(hash ^ value.charCodeAt(index), FNV_PRIME) >>> 0;
    }
    return hash;
}

function normalizeSource(value) {
    return value.trim().toLowerCase();
}

function firstTranslation(value) {
    return value.split("/", 1)[0].trim();
}

function parseDictionary(fileName) {
    const filePath = resolve(assetDir, fileName);
    if (!existsSync(filePath)) {
        throw new Error(`Missing QT2020 asset: ${filePath}`);
    }
    const content = readFileSync(filePath, "utf8").replace(/^\uFEFF/, "");
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
        const source = normalizeSource(line.slice(0, delimiter));
        const target = firstTranslation(line.slice(delimiter + 1));
        if (!source || !target || source.length > 0xffff) {
            rejected += 1;
            continue;
        }
        parsed.push({ source, target });
    }
    return { parsed, rejected, bytes: statSync(filePath).size };
}

const entries = new Map();
const sources = [];
let rejectedLines = 0;
for (const sourceFile of SOURCE_FILES) {
    const result = parseDictionary(sourceFile.name);
    rejectedLines += result.rejected;
    let accepted = 0;
    for (const entry of result.parsed) {
        if (entries.has(entry.source)) continue;
        entries.set(entry.source, entry.target);
        accepted += 1;
    }
    sources.push({
        ...sourceFile,
        bytes: result.bytes,
        parsedEntries: result.parsed.length,
        acceptedEntries: accepted,
        rejectedLines: result.rejected,
    });
}

let bucketCount = 1;
while (entries.size / bucketCount > MAX_LOAD_FACTOR) {
    bucketCount *= 2;
}

const bucketBytes = bucketCount * 4;
const blobOffset = HEADER_SIZE + bucketBytes;
const buckets = Buffer.alloc(bucketBytes);
const encodedEntries = [];
let cursor = blobOffset;
let maxSourceChars = 0;
let collisions = 0;

for (const [source, target] of entries) {
    const sourceBytes = Buffer.from(source, "utf16le");
    const targetBytes = Buffer.from(target, "utf8");
    const hash = fnv1aUtf16(source);
    const entry = Buffer.allocUnsafe(12 + sourceBytes.length + targetBytes.length);
    entry.writeUInt32LE(hash, 0);
    entry.writeUInt16LE(source.length, 4);
    entry.writeUInt16LE(0, 6);
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
header.writeUInt32LE(0, 28);

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

const indexBytes = readFileSync(outputPath);
const manifest = {
    id: "quick-translator-2020-debug",
    sourceRevision: "2025-09-01",
    formatVersion: FORMAT_VERSION,
    entryCount: entries.size,
    maxSourceChars,
    bucketCount,
    loadFactor: Number((entries.size / bucketCount).toFixed(6)),
    collisions,
    rejectedLines,
    index: {
        file: "qt2020-terms.qtdict",
        bytes: indexBytes.length,
        sha256: createHash("sha256").update(indexBytes).digest("hex"),
    },
    sources,
};
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

console.log(
    JSON.stringify(
        {
            outputPath,
            manifestPath,
            entryCount: entries.size,
            maxSourceChars,
            bucketCount,
            indexBytes: indexBytes.length,
            sha256: manifest.index.sha256,
        },
        null,
        2,
    ),
);

---
title: Use Post Upsert and Pre-Approved Tool Policy
type: decision
slug: use-post-upsert-and-pre-approved-tool-policy
category: decisions
created: 2026-10-04T15:49:10
updated: 2026-10-04T15:49:10
status: active
source: /plan
tags: ["p59", "architecture", "decision", "auto"]
---

# Use Post Upsert and Pre-Approved Tool Policy

Context: Supabase PUT returned NoSuchKey on non-existent storage objects; Chatbot required repeated manual approvals causing UI locks; Antigravity 403 dropped validation URL. Rationale: Use POST with x-upsert: true for Supabase uploads; provide user-configurable toggleable tool permissions with auto-approval for seamless agent execution; extract and surface validation_url for Google Cloud Code challenges. Alternatives: Manual upload checks, dialog prompt per tool call. Impact: Eliminates sync errors, unblocks chatbot workflows, improves UX.

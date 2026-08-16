# ADR-004 - Workspace navigation

- Status: Accepted
- Date: 2026-07-29
- Owners: P02

## Context

Top-level navigation hien phan tan loi tat Sang tac, Ebook, Agent va RSS; Browser dang la shortcut/top-level khong co mot hanh dong ro de ve UI app.

## Decision

1. Top-level chi giu cac destination thuong xuyen theo form factor; Sang tac, Bien tap Ebook, Agent va RSS Sources nam trong mot `Workspace` destination.
2. Workspace la Compose/MVI page, moi module la mot action row/tile co status va recent context; khong embed full module screen vao card.
3. Browser khong con la item thanh dieu huong. Browser duoc mo tu source/context/search/link va co action `Exit browser` luon hien trong toolbar/menu, pop ve route app da mo Browser.
4. Browser state la nested route state: tabs/history/source context duoc restore sau rotation/process recreation, nhung `Exit browser` xoa presentation route, khong xoa tabs/bookmarks/cookies.
5. Back trong Browser dieu huong WebView history truoc, sau do tab history, cuoi cung ve app. Predictive back preview phai phan anh dung cap.
6. Phone dung bottom bar voi toi da 5 top-level items; expanded width dung navigation rail. Cung route keys va selection state, khong duplicate navigator.

## Public contract

- Mot Workspace route duy nhat cho 4 module.
- Browser khong co top-level nav ID.
- `Exit browser` khac Android Back va luon tra ve app route gan nhat.
- Deep link/import van co the mo module truc tiep ma khong pha Workspace back stack.

## Alternatives

- Giu moi module top-level: loai bo vi thanh dieu huong qua tai.
- Browser la mot tab chinh: loai bo theo yeu cau nguoi dung.
- Exit ket thuc MainActivity: loai bo vi mat state app.

## Consequences

- Can migration shortcut/deep link va route restoration tests.
- Workspace phai hien status gon, khong bien thanh landing/marketing page.
- Module Activities legacy co the duoc launch qua callback trong giai doan chuyen tiep.

## Rollback

Feature flag co the phuc hoi cac destination cu trong debug/staged rollout. Route keys legacy duoc map sang Workspace/module callback, khong xoa ngay trong release dau.

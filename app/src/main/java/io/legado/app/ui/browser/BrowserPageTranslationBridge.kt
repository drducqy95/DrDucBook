package io.legado.app.ui.browser

import io.legado.app.domain.model.BrowserPageTextNode
import io.legado.app.domain.model.BrowserPageTextTranslation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BrowserPageTranslationBridge {

    const val MUTATION_BRIDGE_NAME = "LegadoBrowserMutationBridge"

    private val json = Json { ignoreUnknownKeys = true }

    fun extractionScript(): String = EXTRACT_SCRIPT

    fun installMutationObserverScript(): String = INSTALL_OBSERVER_SCRIPT

    fun restoreOriginalScript(): String = RESTORE_SCRIPT

    fun decodeSnapshot(evaluationResult: String?): List<BrowserPageTextNode> {
        if (evaluationResult.isNullOrBlank() || evaluationResult == "null") return emptyList()
        val payload = runCatching { json.decodeFromString<String>(evaluationResult) }
            .getOrElse { evaluationResult }
        return runCatching { json.decodeFromString<SnapshotPayload>(payload).nodes }
            .getOrDefault(emptyList())
            .map { node -> BrowserPageTextNode(node.id, node.text, node.contentHash) }
    }

    fun applyTranslationsScript(translations: List<BrowserPageTextTranslation>): String {
        val updates = translations.map { item ->
            TranslationPayload(
                id = item.id,
                originalText = item.originalText,
                translatedText = item.translatedText,
                contentHash = item.contentHash,
            )
        }
        return """
            (function() {
              const updates = ${json.encodeToString(updates)};
              const store = window.__legadoTranslationStore;
              if (!store) return 0;
              window.__legadoTranslationApplying = true;
              let applied = 0;
              try {
                updates.forEach(function(update) {
                  const entry = store.get(update.id);
                  if (!entry || !entry.node || !entry.node.isConnected) return;
                  if (entry.original !== update.originalText || entry.hash !== update.contentHash) return;
                  if (entry.node.nodeValue !== entry.original && entry.node.nodeValue !== entry.translated) return;
                  entry.translated = update.translatedText;
                  entry.node.nodeValue = update.translatedText;
                  applied += 1;
                });
              } finally {
                window.__legadoTranslationApplying = false;
              }
              return applied;
            })();
        """.trimIndent()
    }

    @Serializable
    private data class SnapshotPayload(
        val nodes: List<NodePayload> = emptyList(),
    )

    @Serializable
    private data class NodePayload(
        val id: String,
        val text: String,
        val contentHash: String,
    )

    @Serializable
    private data class TranslationPayload(
        val id: String,
        val originalText: String,
        val translatedText: String,
        val contentHash: String,
    )

    private const val EXTRACT_SCRIPT = """
        (function() {
          if (!document.body) return JSON.stringify({nodes: []});
          const blockedTags = new Set(['SCRIPT','STYLE','NOSCRIPT','TEXTAREA','INPUT','SELECT','OPTION','BUTTON','CODE','PRE']);
          const store = window.__legadoTranslationStore || new Map();
          const nodeIds = window.__legadoTranslationNodeIds || new WeakMap();
          window.__legadoTranslationStore = store;
          window.__legadoTranslationNodeIds = nodeIds;
          window.__legadoTranslationNextId = window.__legadoTranslationNextId || 1;
          const hash = function(value) {
            let h = 2166136261;
            for (let i = 0; i < value.length; i += 1) {
              h ^= value.charCodeAt(i);
              h = Math.imul(h, 16777619);
            }
            return (h >>> 0).toString(16);
          };
          const visible = function(element) {
            if (!element || !element.isConnected) return false;
            const style = window.getComputedStyle(element);
            if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false;
            return element.getClientRects().length > 0;
          };
          const nodes = [];
          let totalChars = 0;
          const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
            acceptNode: function(node) {
              const parent = node.parentElement;
              const text = (node.nodeValue || '').trim();
              if (!parent || text.length < 2 || blockedTags.has(parent.tagName)) return NodeFilter.FILTER_REJECT;
              if (parent.closest('input,textarea,select,option,button,script,style,noscript,code,pre,[contenteditable="true"],[contenteditable=""]')) return NodeFilter.FILTER_REJECT;
              if (!visible(parent)) return NodeFilter.FILTER_REJECT;
              return NodeFilter.FILTER_ACCEPT;
            }
          });
          let node;
          while ((node = walker.nextNode()) && nodes.length < 120 && totalChars < 24000) {
            let id = nodeIds.get(node);
            if (!id) {
              id = 'n' + (window.__legadoTranslationNextId++);
              nodeIds.set(node, id);
            }
            let entry = store.get(id);
            const current = node.nodeValue || '';
            if (entry && entry.translated && current === entry.translated) continue;
            if (!entry || current !== entry.original) {
              entry = {node: node, original: current, translated: null, hash: hash(current)};
              store.set(id, entry);
            }
            if (totalChars + entry.original.length > 24000) break;
            nodes.push({id: id, text: entry.original, contentHash: entry.hash});
            totalChars += entry.original.length;
          }
          return JSON.stringify({nodes: nodes});
        })();
    """

    private const val INSTALL_OBSERVER_SCRIPT = """
        (function() {
          if (!document.body || window.__legadoTranslationObserver) return;
          let timer = null;
          const observer = new MutationObserver(function() {
            if (window.__legadoTranslationApplying) return;
            clearTimeout(timer);
            timer = setTimeout(function() {
              try { window.LegadoBrowserMutationBridge.onMutation(); } catch (_) {}
            }, 700);
          });
          observer.observe(document.body, {subtree: true, childList: true, characterData: true});
          window.__legadoTranslationObserver = observer;
        })();
    """

    private const val RESTORE_SCRIPT = """
        (function() {
          const store = window.__legadoTranslationStore;
          if (!store) return 0;
          window.__legadoTranslationApplying = true;
          let restored = 0;
          try {
            store.forEach(function(entry) {
              if (!entry.node || !entry.node.isConnected || !entry.translated) return;
              if (entry.node.nodeValue === entry.translated) {
                entry.node.nodeValue = entry.original;
                restored += 1;
              }
              entry.translated = null;
            });
          } finally {
            window.__legadoTranslationApplying = false;
          }
          return restored;
        })();
    """
}

<template>
  <div class="markdown-content" v-html="renderedContent" />
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { computed } from 'vue';

const props = defineProps<{
  content: string;
}>();

const renderedContent = computed(() => {
  try {
    const markdown = marked.parse(props.content || '', {
      async: false,
      breaks: true,
      gfm: true,
    });
    return DOMPurify.sanitize(markdown, {
      USE_PROFILES: { html: true },
    });
  } catch {
    return DOMPurify.sanitize(props.content || '');
  }
});
</script>

<style scoped>
.markdown-content {
  color: #1f2937;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.markdown-content :deep(:first-child) { margin-top: 0; }
.markdown-content :deep(:last-child) { margin-bottom: 0; }
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  color: #111827;
  font-weight: 600;
  line-height: 1.35;
  margin: 1.1em 0 0.55em;
}

.markdown-content :deep(h1) { font-size: 1.5em; }
.markdown-content :deep(h2) { font-size: 1.3em; }
.markdown-content :deep(h3) { font-size: 1.15em; }
.markdown-content :deep(p) { margin: 0.7em 0; }
.markdown-content :deep(ul),
.markdown-content :deep(ol) { margin: 0.7em 0; padding-left: 1.6em; }
.markdown-content :deep(li) { margin: 0.25em 0; }
.markdown-content :deep(blockquote) {
  border-left: 3px solid #d9d9d9;
  color: #6b7280;
  margin: 0.9em 0;
  padding: 0.1em 1em;
}

.markdown-content :deep(code) {
  background: #f5f5f5;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.9em;
  padding: 0.15em 0.35em;
}

.markdown-content :deep(pre) {
  background: #f6f8fa;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  margin: 0.9em 0;
  overflow-x: auto;
  padding: 12px;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  display: block;
  margin: 0.9em 0;
  max-width: 100%;
  overflow-x: auto;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #d9d9d9;
  padding: 6px 10px;
  text-align: left;
}

.markdown-content :deep(th) { background: #fafafa; font-weight: 600; }
.markdown-content :deep(a) { color: #1677ff; }
.markdown-content :deep(hr) { border: 0; border-top: 1px solid #f0f0f0; margin: 1em 0; }
.markdown-content :deep(img) { height: auto; max-width: 100%; }
</style>

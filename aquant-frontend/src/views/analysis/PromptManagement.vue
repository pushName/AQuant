<template>
  <div class="prompt-page">
    <a-row :gutter="16">
      <a-col :xs="24" :md="7">
        <a-card :bordered="false" title="角色与模板">
          <a-typography-paragraph type="secondary">选择角色后可查看历史版本、编辑草稿并发布。发布后的内容只作用于新建作业。</a-typography-paragraph>
          <a-list size="small" :data-source="templates">
            <template #renderItem="{ item }"><a-list-item :class="{ active: item.roleKey === selectedRole }" @click="selectRole(item.roleKey)"><a-space direction="vertical" size="small"><span>{{ roleLabel(item.roleKey) }}</span><a-typography-text type="secondary">{{ item.roleKey }}</a-typography-text><a-tag>当前发布版本 v{{ item.publishedVersion || 1 }}</a-tag></a-space></a-list-item></template>
          </a-list>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="17">
        <a-card :bordered="false" :title="`${selectedRole ? roleLabel(selectedRole) : '请选择角色'}提示词`">
          <template #extra><a-space><a-select v-model:value="selectedVersion" style="width:160px" @change="loadVersion"><a-select-option v-for="version in versions" :key="version.version" :value="version.version">版本 {{ version.version }} · {{ versionStatusLabel(version.status) }}</a-select-option></a-select><a-button :loading="syncing" @click="syncSourceDefaults">同步 Python 默认提示词</a-button><a-button type="primary" :loading="saving" @click="save">保存为新草稿</a-button><a-button :disabled="!selectedVersion" @click="publish">发布当前版本</a-button></a-space></template>
          <a-alert message="提示词仅对新作业生效；作业启动时会固定版本快照。" type="info" show-icon style="margin-bottom:16px" />
          <a-alert message="变量使用说明" type="warning" show-icon style="margin-bottom:16px"
            description="以下七个变量均在 Python 服务中运行时渲染。缺少所需运行时值会阻止请求进入模型，避免将未替换占位符发送给上游。debate_round 只能用于辩论节点。" />
          <a-alert message="默认提示词同步" type="info" show-icon style="margin-bottom:16px"
            description="同步会从已启动的 Python 分析服务读取当前 TradingAgents 源码的 16 个角色基线；仅替换系统生成的通用占位模板，已人工发布的提示词不会被覆盖。" />
          <a-form layout="vertical">
            <a-form-item label="提示词内容" extra="使用双大括号包裹变量名引用上下文。系统仅允许已声明变量，且禁止使用 API Key、Token 等凭据变量。">
              <a-textarea v-model:value="content" :rows="22" placeholder="请输入角色提示词，变量使用 {{ticker}}、{{date}} 等形式。" />
            </a-form-item>
          </a-form>
          <a-divider orientation="left">变量含义与适用范围</a-divider>
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item v-for="variable in variableDefinitions" :key="variable.key" :label="`{{${variable.key}}}`">
              <strong>{{ variable.title }}</strong>：{{ variable.description }}
              <a-tag color="blue" style="margin-left: 8px">适用：{{ variable.scope }}</a-tag>
              <a-tag color="green" style="margin-left: 8px">当前自动注入</a-tag>
            </a-descriptions-item>
          </a-descriptions>
          <div class="variables">当前版本声明变量：{{ variables.join('、') || '未声明；保存时将按提示词内容提取' }}</div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { importSourcePromptDefaults, listPromptTemplates, listPromptVersions, publishPromptVersion, savePromptDraft, type PromptTemplate, type PromptVersion } from '@/api/analysis';
import { analysisRoleLabel, PROMPT_VARIABLES } from '@/constants/analysis';

const templates = ref<PromptTemplate[]>([]);
const versions = ref<PromptVersion[]>([]);
const selectedRole = ref('');
const selectedVersion = ref<number>();
const content = ref('');
const variables = ref<string[]>([]);
const saving = ref(false);
const syncing = ref(false);
const roleLabel = analysisRoleLabel;
const variableDefinitions = PROMPT_VARIABLES;
const versionStatusLabel = (status: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] || status);

const selectRole = async (role: string) => { selectedRole.value = role; await loadVersions(); };
const loadVersions = async () => {
  if (!selectedRole.value) return;
  const response = await listPromptVersions(selectedRole.value);
  versions.value = response.data.data || [];
  selectedVersion.value = versions.value[0]?.version;
  loadVersion();
};
const loadVersion = () => {
  const version = versions.value.find((item) => item.version === selectedVersion.value);
  content.value = version?.content || '';
  variables.value = version?.variables || [];
};
const save = async () => {
  if (!selectedRole.value || !content.value.trim()) { message.warning('提示词内容不能为空'); return; }
  saving.value = true;
  try { await savePromptDraft(selectedRole.value, content.value, variables.value); message.success('草稿已保存'); await loadVersions(); }
  finally { saving.value = false; }
};
const publish = async () => {
  if (!selectedRole.value || !selectedVersion.value) return;
  await publishPromptVersion(selectedRole.value, selectedVersion.value); message.success('提示词已发布'); await loadVersions();
};
const syncSourceDefaults = async () => {
  syncing.value = true;
  try {
    const response = await importSourcePromptDefaults();
    const result = response.data.data || {};
    message.success(`已同步 ${result.importedCount || 0} 个角色；保留 ${result.skippedCount || 0} 个已人工发布版本`);
    const selected = selectedRole.value;
    const templatesResponse = await listPromptTemplates();
    templates.value = templatesResponse.data.data || [];
    if (selected) await selectRole(selected);
  } finally { syncing.value = false; }
};
onMounted(async () => { const response = await listPromptTemplates(); templates.value = response.data.data || []; if (templates.value[0]) await selectRole(templates.value[0].roleKey); });
</script>

<style scoped>
.prompt-page { padding: 8px; }
.active { background: #eef5ff; cursor: pointer; }
.variables { color: #87909c; margin-top: 8px; font-size: 12px; }
:deep(.ant-list-item) { cursor: pointer; }
</style>

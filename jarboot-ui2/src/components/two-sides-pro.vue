<template>
  <div class="flex two-sides-container" ref="containerRef">
    <div :style="{ width: leftWidth + 'px', display: state.collapsed ? 'none' : 'block' }">
      <el-card :body-style="{ padding: 0 } as any">
        <template #header v-if="showHeader">
          <div class="flex header">
            <slot name="left-title">{{ leftTitle }}</slot>
            <div style="flex: auto"></div>
            <slot name="left-tools"></slot>
          </div>
        </template>
        <div :style="{ height: bodyHeight + 'px' }" style="overflow: auto">
          <slot name="left-content"></slot>
        </div>
      </el-card>
    </div>
    <div style="width: 0">
      <div class="_collapse_box" :style="getStyle()" @click="collapse">
        <icon-pro v-if="state.collapsed" icon="CaretRight"></icon-pro>
        <icon-pro v-else icon="CaretLeft"></icon-pro>
      </div>
    </div>
    <div style="flex: auto" :style="{ width: `${totalWidth - leftWidth}px` }">
      <el-card :body-style="{ padding: 0 } as any">
        <template #header v-if="showHeader">
          <div class="flex header">
            <slot name="right-title">{{ rightTitle }}</slot>
            <div style="flex: auto"></div>
            <slot name="right-tools"></slot>
          </div>
        </template>
        <div :style="{ height: bodyHeight + 'px' }" style="overflow: auto">
          <slot name="right-content"></slot>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, watch, ref } from 'vue';

const props = defineProps({
  bodyHeight: { type: Number, default: 100 },
  leftWidth: { type: Number, default: 300 },
  totalWidth: { type: Number, default: 1000 },
  leftTitle: { type: String, default: '左侧标题' },
  rightTitle: { type: String, default: '右侧侧标题' },
  collapsed: { type: Boolean, default: false },
  showHeader: { type: Boolean, default: true },
});
const emit = defineEmits<{
  (e: 'update:collapsed', value: boolean): void;
}>();
const state = reactive({
  collapsed: false,
});
const containerRef = ref<HTMLDivElement>();

watch(
  () => props.collapsed,
  value => (state.collapsed = value)
);

const getStyle = () => {
  if (state.collapsed) {
    return {};
  }
  return { left: '-8px' };
};

function collapse() {
  state.collapsed = !state.collapsed;
  emit('update:collapsed', state.collapsed);
}

onMounted(() => {
  state.collapsed = props.collapsed;
});
</script>

<style lang="less">
.two-sides-container {
  .el-card__header {
    padding: 0 12px;
    height: 44px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
}
</style>
<style lang="less" scoped>
.flex {
  display: flex;
}
._collapse_box {
  background: var(--side-bg-color);
  box-sizing: border-box;
  border: var(--el-border);
  box-shadow: 0 1px 12px 0 rgba(0, 0, 0, 0.08);
  width: 16px;
  height: 60px;
  text-align: center;
  border-radius: 10px;
  position: relative;
  top: 120px;
  color: #c0c4cc;
  display: flex;
  flex-direction: column;
  justify-content: center;
  z-index: 1001;
}
</style>

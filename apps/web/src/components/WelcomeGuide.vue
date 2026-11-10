<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ first: boolean; name: string }>()
const emit = defineEmits<{ close: [] }>()
const pages = [
  { title: '오늘', icon: '◷', description: '오늘의 일정과 할 일을 한눈에 보고, 가장 중요한 일을 바로 추가해요.' },
  { title: '일정', icon: '▦', description: '월·주·일 화면을 오가며 약속과 시간을 정리해요.' },
  { title: '할 일', icon: '☑', description: '할 일을 만들고 완료해요. 실제로 한 시간은 성장에서 따로 기록할 수 있어요.' },
  { title: '성장', icon: '↗', description: '활동 시간, 반복 루틴, 목표와 하루 회고를 모아 나의 변화를 확인해요.' },
  { title: '가계부', icon: '₩', description: '계좌와 수입·지출·이체를 기록하고 월별 예산을 살펴봐요.' },
  { title: '투자', icon: '▥', description: '관심종목과 매매를 기록해요. 가격은 직접 입력하며 실시간 시세는 제공하지 않아요.' },
]
const step = ref(props.first ? -1 : 0)
function next() { if (step.value === pages.length - 1) emit('close'); else step.value++ }
</script>

<template>
  <q-dialog :model-value="true" @hide="emit('close')">
    <div class="guide-dialog panel" aria-labelledby="guide-title" aria-describedby="guide-description">
      <template v-if="step === -1">
        <div class="guide-celebration" aria-hidden="true"><span v-for="n in 18" :key="n" class="guide-confetti" :style="{ '--i': n }"/><span class="guide-trophy">✦</span></div>
        <div class="eyebrow">WELCOME TO MY TODAY</div>
        <h2 id="guide-title">{{ name }}님, 시작을 축하해요!</h2>
        <p id="guide-description">이제 나만의 오늘을 만들어 갈 수 있어요.<br>작은 기록 하나부터 시작해 볼까요?</p>
      </template>
      <template v-else>
        <div class="eyebrow">MY TODAY 사용 가이드 · {{ step + 1 }} / {{ pages.length }}</div>
        <div class="guide-icon" aria-hidden="true">{{ pages[step]!.icon }}</div>
        <h2 id="guide-title">{{ pages[step]!.title }}</h2>
        <p id="guide-description">{{ pages[step]!.description }}</p>
        <div class="guide-dots" aria-hidden="true"><span v-for="(_, index) in pages" :key="index" :class="{ current: index === step }"/></div>
      </template>
      <div class="guide-actions">
        <q-btn v-if="step >= 0" flat label="이전" :disable="step === 0" @click="step--" />
        <q-btn flat label="나중에" @click="emit('close')" />
        <q-btn color="primary" unelevated :label="step === -1 ? '사용 가이드 보기' : step === pages.length - 1 ? '시작하기' : '다음'" @click="next" />
      </div>
    </div>
  </q-dialog>
</template>

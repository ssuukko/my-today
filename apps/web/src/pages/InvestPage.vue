<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSession } from '../stores/session'
import { usePersonalData } from '../composables/usePersonalData'
import { localDateTime, wallTimeToInstant } from '../planningDates'
import { decimal } from '../decimal'
import { currencies, type Instrument, type Investment, type Trade } from '../api/life'

const timezone = useSession().session!.user.timezone
const { data, loading, busy, error, notice, load, mutate } = usePersonalData<Investment>(() => '/invest')
const editor = ref<'instrument' | 'quote' | 'trade' | null>(null)
const exchange = ref('KRX'), symbol = ref(''), name = ref(''), currency = ref('KRW'), instrumentId = ref('')
const price = ref(''), quantity = ref(''), fees = ref('0'), tax = ref('0'), side = ref('BUY'), time = ref(''), requestId = ref('')
const voiding = ref<Trade | null>(null), deleting = ref<Instrument | null>(null)
const instrumentOptions = computed(() => data.value?.instruments.map(i => ({label:`${i.name} (${i.exchange}:${i.symbol} · ${i.currency})`,value:i.id})) || [])
function instrument(id: string) { return data.value?.instruments.find(i => i.id === id) }
function timestamp(value: string | null) {
  return value ? new Intl.DateTimeFormat('ko-KR', {dateStyle:'medium',timeStyle:'short',timeZone:timezone}).format(new Date(value)) : '미제공'
}
function edit(kind: 'instrument' | 'quote' | 'trade', item?: Instrument) {
  error.value = ''; editor.value = kind; exchange.value = 'KRX'; symbol.value = ''; name.value = ''; currency.value = 'KRW'
  instrumentId.value = item?.id || data.value?.instruments[0]?.id || ''; price.value = kind === 'quote' ? item?.price || '' : ''
  quantity.value = ''; fees.value = '0'; tax.value = '0'; side.value = 'BUY'
  time.value = localDateTime(new Date(),timezone).slice(0,16); requestId.value = crypto.randomUUID()
}
async function save() {
  let path: string, body: unknown
  if (editor.value === 'instrument') {
    path = '/invest/instruments'; body = {exchange:exchange.value.trim(),symbol:symbol.value.trim(),name:name.value.trim(),currency:currency.value}
  } else {
    let instant: string
    try { instant = wallTimeToInstant(time.value,timezone) }
    catch (cause) { error.value = cause instanceof Error ? cause.message : '시각을 확인해 주세요.'; return }
    if (editor.value === 'quote') { path = `/invest/instruments/${instrumentId.value}/quote`; body = {price:price.value,quotedAt:instant} }
    else { path = '/invest/trades'; body = {requestId:requestId.value,instrumentId:instrumentId.value,side:side.value,quantity:quantity.value,price:price.value,fees:fees.value,tax:tax.value,tradedAt:instant} }
  }
  if (await mutate(path,editor.value === 'quote' ? 'PUT' : 'POST',body)) editor.value = null
}
async function voidTrade() { if (voiding.value && await mutate(`/invest/trades/${voiding.value.id}/void`,'POST')) voiding.value = null }
async function removeInstrument() { if (deleting.value && await mutate(`/invest/instruments/${deleting.value.id}`,'DELETE')) deleting.value = null }
function latest(trade: Trade) { return data.value?.trades.find(t => t.instrumentId === trade.instrumentId && !t.voided)?.id === trade.id }
void load()
</script>
<template>
  <div class="page-heading planning-heading"><div><div class="eyebrow">MY INVESTMENTS</div><h1>내 투자 기록을 한눈에</h1><p>관심종목과 수동 매매 기록을 관리해요.</p></div><div class="planning-actions"><q-btn label="종목 추가" outline color="primary" :disable="busy || loading" @click="edit('instrument')"/><q-btn label="매매 기록" color="primary" :disable="busy || loading || !data?.instruments.length" @click="edit('trade')"/></div></div>
  <div class="daily-note"><div><strong>수동 기록 · 수동 가격 기준 추정</strong><p>증권사 주문이나 실시간 시세에 연결되지 않습니다. 손익은 이동평균 원가로 계산하며, 가계부 잔액과 합산하지 않습니다.</p></div></div>
  <div v-if="error" role="alert" class="error-message">{{ error }} <q-btn flat label="다시 시도" :disable="busy" @click="load"/></div><p v-if="notice" role="status" class="success-message">{{ notice }}</p>
  <div v-if="loading" role="status" class="planning-loading"><q-spinner/> 투자 기록을 불러오고 있어요.</div>
  <template v-if="data">
    <section class="panel feature-section"><h2>통화별 보유 현황</h2><p v-if="!data.totals.length" class="feature-empty">보유 기록이 없어요. 종목을 등록하고 매매 내역을 남겨 보세요.</p><div class="feature-currency-grid"><article v-for="total in data.totals" :key="total.currency" class="feature-total"><h3>{{ total.currency }}</h3><dl class="feature-values"><div><dt>남은 취득 원가</dt><dd>{{ decimal(total.remainingCost,total.currency) }}</dd></div><div><dt>수동 평가액</dt><dd>{{ decimal(total.marketValue,total.currency) }}</dd></div><div><dt>평가 손익</dt><dd>{{ decimal(total.unrealizedPnl,total.currency) }}</dd></div><div><dt>실현 손익</dt><dd>{{ decimal(total.realizedPnl,total.currency) }}</dd></div></dl><p v-if="total.unpricedCount" class="field-hint">가격 미입력 {{ total.unpricedCount }}종목. 합계 평가액을 제공하지 않습니다.</p></article></div></section>
    <section class="panel planning-list feature-section"><div class="panel-heading"><h2>관심종목 · 보유 내역</h2><span class="tag">수동 가격</span></div><p v-if="!data.instruments.length" class="feature-empty">첫 관심종목을 추가해 보세요.</p><article v-for="item in data.instruments" :key="item.id" class="feature-holding"><div class="panel-heading"><div><h3>{{ item.name }}</h3><small>{{ item.exchange }}:{{ item.symbol }} · {{ item.currency }}</small></div><div class="planning-row-actions"><q-btn label="수동 가격 입력" flat :aria-label="`${item.name} 수동 가격 입력`" :disable="busy" @click="edit('quote',item)"/><q-btn v-if="!data.trades.some(t => t.instrumentId === item.id) && item.quantity === '0'" label="종목 삭제" flat :aria-label="`${item.name} 종목 삭제`" :disable="busy" @click="deleting = item"/></div></div><dl class="feature-values"><div><dt>보유 수량</dt><dd>{{ decimal(item.quantity) }}</dd></div><div><dt>남은 취득 원가</dt><dd>{{ decimal(item.remainingCost,item.currency) }}</dd></div><div><dt>수동 가격</dt><dd>{{ decimal(item.price,item.currency) }}</dd></div><div><dt>수동 평가액</dt><dd>{{ decimal(item.marketValue,item.currency) }}</dd></div><div><dt>평가 손익</dt><dd>{{ decimal(item.unrealizedPnl,item.currency) }}</dd></div><div><dt>실현 손익</dt><dd>{{ decimal(item.realizedPnl,item.currency) }}</dd></div></dl><p class="field-hint">{{ item.quoteType === 'MANUAL' ? `수동 입력 · 가격 기준 ${timestamp(item.quotedAt)} (${timezone})` : '가격 미입력 · 평가액 미제공' }}</p></article></section>
    <section class="panel planning-list"><div class="panel-heading"><h2>매매 내역</h2><span class="tag">최근 200건</span></div><p v-if="!data.trades.length" class="feature-empty">아직 매매 기록이 없어요.</p><article v-for="item in data.trades" :key="item.id" class="planning-row" :class="{'feature-voided':item.voided}"><div class="planning-row-content"><strong>{{ instrument(item.instrumentId)?.name }} · {{ item.side === 'BUY' ? '매수' : '매도' }}{{ item.voided ? ' · 취소됨' : '' }}</strong><small>{{ timestamp(item.tradedAt) }} · {{ decimal(item.quantity) }}주 × {{ decimal(item.price,instrument(item.instrumentId)?.currency) }}</small><small>수수료 {{ decimal(item.fees) }} · 세금 {{ decimal(item.tax) }}<span v-if="item.side === 'SELL'"> · 실현 손익 {{ decimal(item.realizedPnl,instrument(item.instrumentId)?.currency) }}</span></small></div><q-btn v-if="!item.voided && latest(item)" label="최근 매매 취소" flat :aria-label="`${instrument(item.instrumentId)?.name} 최근 매매 취소`" :disable="busy" @click="voiding = item"/></article><p class="field-hint">정정은 해당 종목의 마지막 매매를 취소한 뒤 다시 기록합니다. 배당·분할·환율·증권 현금 계좌 연동은 포함하지 않습니다.</p></section>
  </template>
  <q-dialog :model-value="!!editor" :persistent="busy" @hide="editor = null"><div class="panel planning-dialog"><h2>{{ editor === 'instrument' ? '관심종목 추가' : editor === 'quote' ? '수동 가격 입력' : '매매 기록' }}</h2><form class="planning-form" @submit.prevent="save"><div v-if="error" role="alert" class="error-message">{{ error }}</div>
    <template v-if="editor === 'instrument'"><q-input v-model="exchange" label="거래소" outlined required maxlength="20" :disable="busy"/><q-input v-model="symbol" label="종목 코드" outlined required maxlength="20" :disable="busy" autofocus/><q-input v-model="name" label="종목 이름" outlined required maxlength="100" :disable="busy"/><q-select v-model="currency" label="통화" :options="currencies" outlined :disable="busy"/></template>
    <template v-else><q-select v-model="instrumentId" label="종목" :options="instrumentOptions" emit-value map-options outlined required :disable="busy || editor === 'quote'"/><template v-if="editor === 'trade'"><q-select v-model="side" label="매매 구분" :options="[{label:'매수',value:'BUY'},{label:'매도',value:'SELL'}]" emit-value map-options outlined :disable="busy"/><q-input v-model="quantity" label="수량" outlined inputmode="decimal" required :disable="busy"/></template><q-input v-model="price" :label="editor === 'quote' ? '수동 가격' : '단가'" inputmode="decimal" outlined required :disable="busy"/><template v-if="editor === 'trade'"><div class="planning-form-grid"><q-input v-model="fees" label="수수료" inputmode="decimal" outlined required :disable="busy"/><q-input v-model="tax" label="세금" inputmode="decimal" outlined required :disable="busy"/></div></template><q-input v-model="time" type="datetime-local" :label="editor === 'quote' ? '가격 기준 시각' : '거래 시각'" outlined required :disable="busy"/><p class="field-hint">{{ timezone }} 기준. 금액·수량은 소수점 6자리까지 입력합니다. 매매는 시간순으로 기록하며 보유 수량을 초과해 매도할 수 없습니다.</p></template>
    <div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="editor = null"/><q-btn type="submit" label="저장" color="primary" :loading="busy"/></div></form></div></q-dialog>
  <q-dialog :model-value="!!voiding || !!deleting" :persistent="busy" @hide="voiding = null; deleting = null"><div class="panel planning-dialog"><h2>{{ deleting ? '관심종목 삭제' : '최근 매매 취소' }}</h2><p>{{ deleting ? `${deleting.name} 관심종목을 삭제할까요?` : '마지막 매매를 취소하고 보유 수량과 원가를 다시 계산할까요? 취소 이력은 보존됩니다.' }}</p><p v-if="error" role="alert" class="error-message">{{ error }}</p><div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="voiding = null; deleting = null"/><q-btn :label="deleting ? '삭제 확인' : '취소 확인'" color="negative" :loading="busy" @click="deleting ? removeInstrument() : voidTrade()"/></div></div></q-dialog>
</template>

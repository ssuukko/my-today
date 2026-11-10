<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useSession } from '../stores/session'
import { usePersonalData } from '../composables/usePersonalData'
import { dateInZone } from '../planningDates'
import { decimal } from '../decimal'
import { currencies, type Budget, type Money, type MoneyTransaction } from '../api/life'

const today = dateInZone(new Date(), useSession().session!.user.timezone)
const month = ref(today.slice(0, 7))
const { data, loading, busy, error, notice, load, mutate } = usePersonalData<Money>(() => `/money?month=${month.value}`)
const editor = ref<'account' | 'transaction' | 'budget' | null>(null)
const name = ref(''), accountType = ref('CHECKING'), currency = ref('KRW'), openingBalance = ref('0')
const transactionType = ref<'EXPENSE' | 'INCOME' | 'TRANSFER'>('EXPENSE'), accountId = ref(''), targetId = ref('')
const amount = ref(''), category = ref('식비'), transactionDate = ref(today), memo = ref('')
const requestId = ref(''), budgetVersion = ref<number | null>(null), budgetMonth = ref(month.value)
const reversing = ref<MoneyTransaction | null>(null)
const accountTypes = [{label:'은행 입출금',value:'CHECKING'},{label:'현금',value:'CASH'},{label:'저축',value:'SAVINGS'},{label:'신용카드 부채',value:'CREDIT_CARD_LIABILITY'},{label:'대출 부채',value:'LOAN_LIABILITY'},{label:'증권 현금',value:'BROKER_CASH'},{label:'기타 자산',value:'OTHER_ASSET'}]
const typeLabels: Record<string, string> = { EXPENSE: '지출', INCOME: '수입', TRANSFER: '이체', REVERSAL: '취소 분개' }
const accountOptions = computed(() => data.value?.accounts.map(a => ({ label: `${a.name} (${a.currency})`, value: a.id })) || [])
const targetOptions = computed(() => data.value?.accounts.filter(a => a.id !== accountId.value && a.currency === data.value?.accounts.find(v => v.id === accountId.value)?.currency).map(a => ({ label: `${a.name} (${a.currency})`, value: a.id })) || [])
watch(month, () => { notice.value = ''; void load() }, { immediate: true })
watch(accountId, () => { targetId.value = '' })
function edit(kind: 'account' | 'transaction' | 'budget', budget?: Budget) {
  editor.value = kind; error.value = ''; name.value = ''; accountType.value = 'CHECKING'; openingBalance.value = '0'
  currency.value = budget?.currency || 'KRW'; amount.value = budget?.amount || ''; category.value = budget?.category || '식비'
  budgetVersion.value = budget?.version ?? null; budgetMonth.value = month.value
  accountId.value = data.value?.accounts[0]?.id || ''; targetId.value = ''; transactionType.value = 'EXPENSE'
  transactionDate.value = month.value === today.slice(0, 7) ? today : `${month.value}-01`; memo.value = ''; requestId.value = crypto.randomUUID()
}
async function save() {
  const kind = editor.value
  const body = kind === 'account'
    ? { name: name.value.trim(), type: accountType.value, currency: currency.value, openingBalance: openingBalance.value }
    : kind === 'budget'
      ? { month: budgetMonth.value, category: category.value.trim(), currency: currency.value, amount: amount.value, ...(budgetVersion.value === null ? {} : { version: budgetVersion.value }) }
      : { requestId: requestId.value, type: transactionType.value, accountId: accountId.value, targetAccountId: transactionType.value === 'TRANSFER' ? targetId.value : null, amount: amount.value, category: transactionType.value === 'TRANSFER' ? null : category.value.trim(), transactionDate: transactionDate.value, memo: memo.value || null }
  if (await mutate(`/money/${kind === 'account' ? 'accounts' : kind === 'budget' ? 'budgets' : 'transactions'}`, kind === 'budget' ? 'PUT' : 'POST', body)) editor.value = null
}
async function reverse() {
  if (reversing.value && await mutate(`/money/transactions/${reversing.value.id}/reverse`, 'POST')) reversing.value = null
}
function accountName(id: string | null) { return data.value?.accounts.find(a => a.id === id)?.name || '' }
</script>
<template>
  <div class="page-heading planning-heading"><div><div class="eyebrow">MY MONEY</div><h1>돈의 흐름을 차분하게</h1><p>확정된 수입·지출·이체를 계좌별로 기록해요.</p></div><div class="planning-actions"><q-btn label="계좌 추가" outline color="primary" :disable="busy || loading" @click="edit('account')"/><q-btn label="거래 기록" color="primary" :disable="busy || loading || !data?.accounts.length" @click="edit('transaction')"/></div></div>
  <div class="panel feature-toolbar"><q-input v-model="month" type="text" placeholder="YYYY-MM" mask="####-##" pattern="[0-9]{4}-[0-9]{2}" label="조회 월" outlined required :disable="busy"/><span>통화별 집계 · 이체는 수입·지출에서 제외</span></div>
  <div v-if="error" role="alert" class="error-message">{{ error }} <q-btn flat label="다시 시도" :disable="busy" @click="load"/></div><p v-if="notice" role="status" class="success-message">{{ notice }}</p>
  <div v-if="loading" class="planning-loading" role="status"><q-spinner/> 가계부를 불러오고 있어요.</div>
  <template v-if="data">
    <section class="panel feature-section"><div class="panel-heading"><h2>월간 흐름</h2></div><p v-if="!data.totals.length" class="feature-empty">이번 달 확정 거래가 없어요.</p><div class="feature-currency-grid"><article v-for="total in data.totals" :key="total.currency" class="feature-total"><h3>{{ total.currency }}</h3><dl class="feature-values"><div><dt>수입</dt><dd>{{ decimal(total.income,total.currency) }}</dd></div><div><dt>지출</dt><dd>{{ decimal(total.expense,total.currency) }}</dd></div><div><dt>순유입</dt><dd>{{ decimal(total.net,total.currency) }}</dd></div></dl></article></div></section>
    <div class="dashboard-grid feature-grid"><section class="panel planning-list"><div class="panel-heading"><h2>계좌와 잔액</h2></div><p v-if="!data.accounts.length" class="feature-empty">계좌를 추가하고 시작 잔액을 입력해 주세요.</p><article v-for="item in data.accounts" :key="item.id" class="planning-row"><div class="planning-row-content"><strong>{{ item.name }}</strong><small>{{ accountTypes.find(t => t.value === item.type)?.label }} · {{ item.currency }}</small></div><strong class="feature-amount">{{ decimal(item.balance,item.currency) }}</strong></article><p class="field-hint">잔액 = 시작 잔액 + 원장 증감. 부채는 음수이며 투자 평가액은 합산하지 않습니다.</p></section>
    <section class="panel planning-list"><div class="panel-heading"><h2>카테고리 예산</h2><q-btn label="예산 추가" flat :disable="busy" @click="edit('budget')"/></div><p v-if="!data.budgets.length" class="feature-empty">월별 예산을 정하고 지출과 비교해 보세요.</p><article v-for="item in data.budgets" :key="item.id" class="planning-row"><div class="planning-row-content"><strong>{{ item.category }} · {{ item.currency }}</strong><small>예산 {{ decimal(item.amount,item.currency) }}</small><span>사용 {{ decimal(item.spent,item.currency) }}</span><small>잔여 {{ decimal(item.remaining,item.currency) }}</small></div><q-btn label="수정" flat :aria-label="`${item.category} 예산 수정`" :disable="busy" @click="edit('budget',item)"/></article><p class="field-hint">예산 잔여액과 실제 계좌 잔액은 서로 다른 값입니다.</p></section></div>
    <section class="panel planning-list feature-section"><div class="panel-heading"><h2>거래 내역</h2><span class="tag">{{ data.month }}</span></div><p v-if="!data.transactions.length" class="feature-empty">아직 거래가 없어요. 실제 거래를 기록해 보세요.</p><article v-for="item in data.transactions" :key="item.id" class="planning-row" :class="{'feature-voided':item.status === 'VOID'}"><div class="planning-row-content"><strong>{{ typeLabels[item.type] }} · {{ item.category || `${accountName(item.accountId)} → ${accountName(item.targetAccountId)}` }}</strong><small>{{ item.transactionDate }} · {{ accountName(item.accountId) }}<span v-if="item.status === 'VOID'"> · 취소됨</span></small><small v-if="item.memo" class="planning-notes">{{ item.memo }}</small></div><div class="planning-row-actions"><strong class="feature-amount">{{ decimal(item.amount,item.currency) }}</strong><q-btn v-if="item.status === 'POSTED' && !item.reversalOf" flat label="거래 취소" :aria-label="`${item.category || '이체'} ${item.transactionDate} 거래 취소`" :disable="busy" @click="reversing = item"/></div></article><p v-if="data.hasMore" role="status" class="field-hint">최근 200건을 표시합니다. 월간 집계에는 전체 거래가 포함됩니다.</p></section>
  </template>
  <q-dialog :model-value="!!editor" :persistent="busy" @hide="editor = null"><div class="panel planning-dialog"><h2>{{ editor === 'account' ? '계좌 추가' : editor === 'budget' ? '월별 예산' : '거래 기록' }}</h2><form class="planning-form" @submit.prevent="save"><div v-if="error" role="alert" class="error-message">{{ error }}</div>
    <template v-if="editor === 'account'"><q-input v-model="name" label="계좌 이름" outlined required maxlength="100" :disable="busy" autofocus/><q-select v-model="accountType" label="계좌 종류" :options="accountTypes" emit-value map-options outlined :disable="busy"/><q-select v-model="currency" label="통화" :options="currencies" outlined :disable="busy"/><q-input v-model="openingBalance" label="시작 잔액" inputmode="decimal" outlined required :disable="busy"/><p class="field-hint">부채는 음수로 입력합니다. 원장을 보존하기 위해 계좌 생성 후 시작 잔액을 바꾸지 않습니다.</p></template>
    <template v-else-if="editor === 'budget'"><q-input v-model="budgetMonth" label="예산 월" type="text" placeholder="YYYY-MM" mask="####-##" pattern="[0-9]{4}-[0-9]{2}" outlined required :disable="busy || budgetVersion !== null"/><q-input v-model="category" label="카테고리" outlined required maxlength="60" :disable="busy || budgetVersion !== null"/><q-select v-model="currency" label="통화" :options="currencies" outlined :disable="busy || budgetVersion !== null"/><q-input v-model="amount" label="예산 금액" inputmode="decimal" outlined required :disable="busy"/></template>
    <template v-else><q-select v-model="transactionType" label="거래 종류" :options="[{label:'지출',value:'EXPENSE'},{label:'수입',value:'INCOME'},{label:'이체·카드대금 납부',value:'TRANSFER'}]" emit-value map-options outlined :disable="busy"/><q-select v-model="accountId" :label="transactionType === 'TRANSFER' ? '보내는 계좌' : '계좌'" :options="accountOptions" emit-value map-options outlined required :disable="busy"/><q-select v-if="transactionType === 'TRANSFER'" v-model="targetId" label="받는 계좌" :options="targetOptions" emit-value map-options outlined required :disable="busy"/><q-input v-else v-model="category" label="카테고리" outlined required maxlength="60" :disable="busy"/><q-input v-model="amount" label="거래 금액" inputmode="decimal" outlined required :disable="busy"/><q-input v-model="transactionDate" label="거래 날짜" type="date" :max="today" outlined required :disable="busy"/><q-input v-model="memo" label="메모" type="textarea" maxlength="1000" outlined :disable="busy"/><p class="field-hint">실제 확정 거래만 저장합니다. 소수점 6자리까지 지원하며 통화가 다른 계좌 간 이체는 지원하지 않습니다.</p></template>
    <div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="editor = null"/><q-btn type="submit" label="저장" color="primary" :loading="busy"/></div></form></div></q-dialog>
  <q-dialog :model-value="!!reversing" :persistent="busy" @hide="reversing = null"><div class="panel planning-dialog"><h2>거래 전체 취소</h2><p>{{ decimal(reversing?.amount,reversing?.currency) }} 거래를 취소할까요? 원거래와 반대 분개를 남기고 잔액·예산을 다시 계산합니다.</p><p v-if="error" role="alert" class="error-message">{{ error }}</p><div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="reversing = null"/><q-btn label="취소 확인" color="negative" :loading="busy" @click="reverse"/></div></div></q-dialog>
</template>

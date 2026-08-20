<template>
  <div
    class="web-service-shell"
    :class="{ 'has-web-service-background': Boolean(clientBackground.imageUrl) }"
    :style="backgroundStyle"
  >
    <div class="web-service-background-layer"></div>
    <div class="web-service-background-dim"></div>
    <div class="web-service-content">
      <WebAppShell><router-view /></WebAppShell>
    </div>
    <el-dialog
      :model-value="pairingRequired"
      :title="`Ghép đôi với ${serviceName}`"
      width="min(92vw, 420px)"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <p>Nhập mã 6 số đang hiển thị trong app để dùng dịch vụ web online.</p>
      <el-input
        v-model="pairingCode"
        inputmode="numeric"
        maxlength="7"
        placeholder="Mã ghép đôi"
        @keyup.enter="submitPairingCode"
      />
      <p v-if="pairingError" class="pairing-error">{{ pairingError }}</p>
      <template #footer>
        <el-button
          type="primary"
          :loading="pairingBusy"
          @click="submitPairingCode"
        >
          Đồng ý
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import {
  computed,
  onMounted,
  ref,
} from 'vue'
import {
  exchangeWebServicePairingCode,
  getWebServiceInstance,
  getWebServiceSession,
} from '@/api/webService'
import {
  clearWebSessionToken,
  getWebSessionToken,
} from '@/api/webSession'
import { useWebServiceStore } from '@/store'
import WebAppShell from '@/components/WebAppShell.vue'
import {
  clientBackground,
  initializeClientBackground,
} from '@/utils/clientPreferences'
import '@/assets/web-service.css'

const webServiceStore = useWebServiceStore()
const pairingRequired = ref(false)
const pairingCode = ref('')
const pairingError = ref('')
const pairingBusy = ref(false)
const serviceName = computed(() => webServiceStore.instance?.serviceName || webServiceStore.instance?.appName || 'DrDucBook')

const backgroundStyle = computed<CSSProperties>(() => ({
  '--web-service-background-image': clientBackground.imageUrl
    ? `url("${clientBackground.imageUrl}")`
    : 'none',
  '--web-service-background-fit': clientBackground.preference.fit,
  '--web-service-background-position': clientBackground.preference.position,
  '--web-service-background-dim': `${clientBackground.preference.dim}`,
  '--web-service-background-blur': `${clientBackground.preference.blur}px`,
}))

const submitPairingCode = async () => {
  pairingBusy.value = true
  pairingError.value = ''
  try {
    await exchangeWebServicePairingCode(pairingCode.value)
    location.reload()
  } catch {
    pairingError.value = 'Mã không đúng, đã hết hạn hoặc đã được sử dụng.'
  } finally {
    pairingBusy.value = false
  }
}

onMounted(async () => {
  await initializeClientBackground()
  try {
    const instance = await getWebServiceInstance()
    webServiceStore.instance = instance
    if (instance.requiresPairing) {
      if (getWebSessionToken()) {
        try {
          await getWebServiceSession()
        } catch {
          clearWebSessionToken()
        }
      }
      pairingRequired.value = !getWebSessionToken()
      if (pairingRequired.value) return
    }
    if (!webServiceStore.policy) await webServiceStore.loadPolicy()
  } catch {}
})

</script>

<style scoped>
.pairing-error {
  color: var(--el-color-danger);
}
</style>

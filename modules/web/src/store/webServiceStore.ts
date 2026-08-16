import { defineStore } from 'pinia'
import {
  deleteWebServiceBackground,
  getWebServiceInstance,
  getWebServicePolicy,
  patchWebServicePolicy,
  resetWebServicePolicy,
  uploadWebServiceBackground,
  type WebServiceInstanceResponse,
  type WebServicePolicy,
  type WebServicePolicyPatch,
} from '@/api/webService'

export const useWebServiceStore = defineStore('webService', {
  state: () => ({
    instance: null as WebServiceInstanceResponse | null,
    policy: null as WebServicePolicy | null,
    policyEtag: '',
    loading: false,
    error: '',
  }),
  actions: {
    async loadInstance() {
      this.instance = await getWebServiceInstance()
      return this.instance
    },
    async loadPolicy() {
      this.loading = true
      try {
        const { policy, etag } = await getWebServicePolicy()
        this.policy = policy
        this.policyEtag = etag
        this.error = ''
        return policy
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error)
        throw error
      } finally {
        this.loading = false
      }
    },
    async patchPolicy(patch: WebServicePolicyPatch) {
      if (!this.policyEtag) await this.loadPolicy()
      const { policy, etag } = await patchWebServicePolicy(
        patch,
        this.policyEtag,
      )
      this.policy = policy
      this.policyEtag = etag
      this.error = ''
      return policy
    },
    async resetPolicy() {
      const { policy, etag } = await resetWebServicePolicy()
      this.policy = policy
      this.policyEtag = etag
      this.error = ''
      return policy
    },
    async uploadBackground(file: File) {
      if (!this.policyEtag) await this.loadPolicy()
      const { policy, etag } = await uploadWebServiceBackground(
        file,
        this.policyEtag,
      )
      this.policy = policy
      this.policyEtag = etag
      this.error = ''
      return policy
    },
    async deleteBackground() {
      if (!this.policyEtag) await this.loadPolicy()
      const { policy, etag } = await deleteWebServiceBackground(this.policyEtag)
      this.policy = policy
      this.policyEtag = etag
      this.error = ''
      return policy
    },
  },
})

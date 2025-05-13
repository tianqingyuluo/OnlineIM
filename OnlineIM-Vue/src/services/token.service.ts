import { authService } from './auth.service'
import { useUserStore } from "@/stores/user.ts";

let tokenServiceInstance: TokenService | null = null

export class TokenService {
  private refreshTimeout: number | null = null
  private token: string = ''
  
  public static init(token: string): TokenService {
    if (!tokenServiceInstance) {
      tokenServiceInstance = new TokenService(token)
      tokenServiceInstance.startAutoRefresh()
    }
    return tokenServiceInstance
  }

  private constructor(initialToken: string) {
    this.token = initialToken
    useUserStore().token = initialToken
  }

  public async updateToken(): Promise<void> {
    try {
      const response = await authService.updateToken(this.token)
      this.token = response.access_token
      useUserStore().token = response.access_token
      this.scheduleNextRefresh((response.expires_in - 30) * 1000)
    } catch (error) {
      console.error('自动刷新Token失败:', error)
      this.stopAutoRefresh()
    }
  }

  private scheduleNextRefresh(delay: number): void {
    this.stopAutoRefresh()
    this.refreshTimeout = window.setTimeout(() => {
      this.updateToken()
    }, delay)
  }

  public startAutoRefresh(): void {
    this.updateToken()
  }

  public stopAutoRefresh(): void {
    if (this.refreshTimeout) {
      window.clearTimeout(this.refreshTimeout)
      this.refreshTimeout = null
    }
  }

  public static getInstance(): TokenService | null {
    return tokenServiceInstance
  }

  public static clear(): void {
    tokenServiceInstance?.stopAutoRefresh()
    tokenServiceInstance = null
  }
}
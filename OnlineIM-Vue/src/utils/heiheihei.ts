// src/utils/simpleMusicPlayer.ts

class SimpleMusicPlayer {
    private audio: HTMLAudioElement | null = null;
    private isPlaying = false;

    /**
     * 初始化音频
     * @param src 音频文件路径
     */
    public init(src: string): void {
        this.audio = new Audio(src);
        this.audio.load();
    }

    /**
     * 播放音乐
     */
    public play(): void {
        if (!this.audio) {
            console.warn('音频未初始化，请先调用init方法');
            return;
        }

        this.audio.play()
            .then(() => {
                this.isPlaying = true;
            })
            .catch(error => {
                console.error('播放失败:', error);
            });
    }

    /**
     * 停止音乐
     */
    public stop(): void {
        if (!this.audio) {
            console.warn('音频未初始化，请先调用init方法');
            return;
        }

        this.audio.pause();
        this.audio.currentTime = 0;
        this.isPlaying = false;
    }

    /**
     * 获取当前播放状态
     * @returns 是否正在播放
     */
    public getPlayingState(): boolean {
        return this.isPlaying;
    }
}

// 导出单例实例
export const simpleMusicPlayer = new SimpleMusicPlayer();
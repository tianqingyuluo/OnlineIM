class SimpleMusicPlayer {
    private audio: HTMLAudioElement | null = null;
    private isPlaying = false;
    private defaultAudioSrc = '/point.mp3'; // 硬编码默认路径

    /**
     * 初始化音频（如果未提供src，则使用默认音频源）
     * @param src 可选，音频文件路径
     */
    public init(src: string = this.defaultAudioSrc): void {
        // 如果已有音频实例，先停止并清理
        if (this.audio) {
            this.stop();
            this.audio.removeEventListener('ended', this.handleAudioEnded);
        }

        // 创建新音频实例
        this.audio = new Audio(src);
        this.audio.addEventListener('ended', this.handleAudioEnded);
        this.audio.load();
    }

    /**
     * 音频播放结束的回调
     */
    private handleAudioEnded = () => {
        this.isPlaying = false; // 播放结束时更新状态
    };

    /**
     * 播放音乐（如果正在播放，先停止再重新播放）
     */
    public play(): void {
        if (!this.audio) {
            // 如果未初始化，尝试用默认音频源初始化
            this.init();
        }

        // 如果仍然没有音频实例，报错
        if (!this.audio) {
            console.warn('音频初始化失败，请检查默认音频文件是否存在');
            return;
        }

        // 如果正在播放，先停止
        if (this.isPlaying) {
            this.audio.pause();
            this.audio.currentTime = 0; // 重置播放位置
        }

        // 开始播放
        this.audio.play()
            .then(() => {
                this.isPlaying = true;
            })
            .catch(error => {
                console.error('播放失败:', error);
                this.isPlaying = false;
            });
    }

    /**
     * 停止音乐
     */
    public stop(): void {
        if (!this.audio) return;
        this.audio.pause();
        this.audio.currentTime = 0;
        this.isPlaying = false;
    }

    /**
     * 获取当前播放状态
     */
    public getPlayingState(): boolean {
        return this.isPlaying;
    }
}

// 导出单例实例
export const simpleMusicPlayer = new SimpleMusicPlayer();
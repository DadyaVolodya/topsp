class PcmWorklet extends AudioWorkletProcessor {
  constructor(options) {
    super();
    const opts = (options && options.processorOptions) || {};
    this.targetRate = opts.targetRate || 16000;
    this.ratio = sampleRate / this.targetRate;
    this.pos = 0;
    this.flushAt = Math.max(320, Math.round(this.targetRate * 0.04));
    this.out = new Int16Array(this.flushAt);
    this.filled = 0;
    this.hpPrevX = 0;
    this.hpPrevY = 0;
    this.gain = 5;
  }

  process(inputs) {
    const input = inputs[0] && inputs[0][0];
    if (!input || !input.length) {
      return true;
    }
    const n = input.length;
    let pos = this.pos;
    while (pos < n) {
      const i0 = pos | 0;
      const frac = pos - i0;
      const s0 = input[i0] || 0;
      const s1 = input[Math.min(i0 + 1, n - 1)] || 0;
      let s = s0 + (s1 - s0) * frac;
      const hp = 0.97 * (this.hpPrevY + s - this.hpPrevX);
      this.hpPrevX = s;
      this.hpPrevY = hp;
      s = hp;
      const mag = Math.abs(s);
      if (mag > 0.018) {
        const want = 0.2 / mag;
        this.gain += (Math.max(1.4, Math.min(14, want)) - this.gain) * 0.02;
      } else {
        this.gain += (7 - this.gain) * 0.003;
      }
      s = Math.max(-1, Math.min(1, s * this.gain));
      this.out[this.filled++] = s < 0 ? s * 0x8000 : s * 0x7fff;
      if (this.filled === this.out.length) {
        const chunk = this.out.slice();
        this.filled = 0;
        this.port.postMessage(chunk.buffer, [chunk.buffer]);
      }
      pos += this.ratio;
    }
    this.pos = pos - n;
    return true;
  }
}

registerProcessor("pcm-worklet", PcmWorklet);

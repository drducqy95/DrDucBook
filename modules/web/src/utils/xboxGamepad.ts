// =====================================================
// 类型定义
// =====================================================

interface GamepadConfig {
  DEBUG: boolean;
  AXIS_THRESHOLD: number;
  AXIS_COOLDOWN: number;
  DPAD_INDEX: {
    UP: number;
    DOWN: number;
    LEFT: number;
    RIGHT: number;
  };
}

interface DPadState {
  up: boolean;
  down: boolean;
  left: boolean;
  right: boolean;
}

interface GlobalState {
  lastAxisTime: number;
  lastAxisDirection: number;
  dpadPressed: DPadState;
}

// =====================================================
// 配置区域
// =====================================================

const CONFIG: GamepadConfig = {
  DEBUG: false,              // 日志总开关
  AXIS_THRESHOLD: 0.7,      // 摇杆触发阈值
  AXIS_COOLDOWN: 300,       // 摇杆触发冷却时间 (ms)
  DPAD_INDEX: {             // Xbox 标准映射
    UP: 12,
    DOWN: 13,
    LEFT: 14,
    RIGHT: 15
  }
};

// =====================================================
// 日志工具函数
// =====================================================

function log(...args: any[]): void {
  if (!CONFIG.DEBUG) return;
  console.log(...args);
}

// =====================================================
// 状态记录
// =====================================================

const state: GlobalState = {
  lastAxisTime: 0,
  lastAxisDirection: 0,
  dpadPressed: {
    up: false,
    down: false,
    left: false,
    right: false
  }
};

// =====================================================
// 通用工具函数
// =====================================================

/**
 * 平滑翻页函数
 * @param direction 1 = 向下, -1 = 向上
 */
function scrollPage(direction: number): void {
  const offset = window.innerHeight - 110;
  const distance = direction === 1 ? offset : -offset;

  log(direction === 1 ? "🎮 Lật trang: xuống" : "🎮 Lật trang: lên");

  window.scrollBy({
    top: distance,
    behavior: "smooth"
  });
}

/**
 * 章节切换函数
 * @param direction 1 = Chương sau, -1 = Chương trước
 */
function switchChapter(direction: number): void {
  const buttons = document.querySelectorAll<HTMLElement>(".read-bar .tool-icon");

  if (buttons.length < 2) return;

  if (direction === 1) {
    log("🎮 Chuyển chương: chương sau");
    buttons[1].click();
  } else {
    log("🎮 Chuyển chương: chương trước");
    buttons[0].click();
  }
}

/**
 * 边沿检测函数
 */
function isPressedOnce(current: boolean, previous: boolean): boolean {
  return current && !previous;
}

// =====================================================
// 摇杆处理
// =====================================================

function handleAxis(gp: Gamepad, now: number): void {
  const axisY = gp.axes[1] || 0;

  if (Math.abs(axisY) <= CONFIG.AXIS_THRESHOLD) {
    state.lastAxisDirection = 0;
    return;
  }

  const direction = axisY > 0 ? 1 : -1;
  const cooldownPassed = now - state.lastAxisTime > CONFIG.AXIS_COOLDOWN;
  const directionChanged = direction !== state.lastAxisDirection;

  if (cooldownPassed || directionChanged) {
    state.lastAxisTime = now;
    state.lastAxisDirection = direction;
    log(`🎮 Joystick kích hoạt | axisY=${axisY.toFixed(2)}`);
    scrollPage(direction);
  }
}

// =====================================================
// DPad 处理
// =====================================================

function handleDPad(gp: Gamepad): void {
  const indexes = CONFIG.DPAD_INDEX;

  const current: DPadState = {
    up: gp.buttons[indexes.UP]?.pressed || false,
    down: gp.buttons[indexes.DOWN]?.pressed || false,
    left: gp.buttons[indexes.LEFT]?.pressed || false,
    right: gp.buttons[indexes.RIGHT]?.pressed || false
  };

  if (isPressedOnce(current.up, state.dpadPressed.up)) {
    log("🎮 DPad lên");
    scrollPage(-1);
  }
  if (isPressedOnce(current.down, state.dpadPressed.down)) {
    log("🎮 DPad xuống");
    scrollPage(1);
  }
  if (isPressedOnce(current.left, state.dpadPressed.left)) {
    log("🎮 DPad trái");
    switchChapter(-1);
  }
  if (isPressedOnce(current.right, state.dpadPressed.right)) {
    log("🎮 DPad phải");
    switchChapter(1);
  }

  state.dpadPressed = current;
}

// =====================================================
// 主手柄处理入口
// =====================================================

function handleGamepad(gp: Gamepad | null): void {
  if (!gp) return;
  const now = performance.now();
  handleAxis(gp, now);
  handleDPad(gp);
}

// =====================================================
// 主循环
// =====================================================

let running = false;

function gamepadLoop(): void {
  const gamepads = navigator.getGamepads?.() || [];

  for (const gp of gamepads) {
    if (gp) handleGamepad(gp);
  }

  requestAnimationFrame(gamepadLoop);
}

// =====================================================
// 连接 / 断开 事件
// =====================================================

window.addEventListener("gamepadconnected", (e: GamepadEvent) => {
  log("🎮 Tay cầm đã kết nối: ", e.gamepad.id);
  if (!running) {
    running = true;
    requestAnimationFrame(gamepadLoop);
  }
});

window.addEventListener("gamepaddisconnected", (e: GamepadEvent) => {
  log("🎮 Tay cầm đã ngắt: ", e.gamepad.id);
});

export function initXboxGamepad(): void {
  if (!running) {
    running = true;
    requestAnimationFrame(gamepadLoop);
  }
}
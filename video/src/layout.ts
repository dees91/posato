// Device frames share the website's bezel geometry (website/src/styles/site.css,
// "Device frames"), expressed as ratios of the frame width so one rule works at
// any size. Capture pixels map to composition pixels through `capturePoint`.

export type Device = "mac" | "iphone";
export type Point = readonly [number, number];

export type FrameSpec = {
  readonly device: Device;
  readonly left: number;
  readonly top: number;
  readonly width: number;
};

export const CAPTURE = {
  mac: { width: 1272, height: 936 },
  iphone: { width: 660, height: 1430 },
} as const;

const MAC = { border: 0.018, padX: 0.06, padY: 0.05, radius: 0.026, windowRadius: 0.0157 };
const IPHONE = { border: 0.035, radius: 0.15 };

export type FrameMetrics = {
  readonly border: number;
  readonly padX: number;
  readonly padY: number;
  readonly radius: number;
  readonly innerWidth: number;
  readonly innerHeight: number;
  readonly height: number;
  readonly scale: number;
  readonly origin: Point;
};

export function frameMetrics(spec: FrameSpec): FrameMetrics {
  const capture = CAPTURE[spec.device];
  if (spec.device === "mac") {
    const border = spec.width * MAC.border;
    const padX = spec.width * MAC.padX;
    const padY = spec.width * MAC.padY;
    const innerWidth = spec.width - 2 * border - 2 * padX;
    const innerHeight = (innerWidth * capture.height) / capture.width;
    return {
      border,
      padX,
      padY,
      radius: spec.width * MAC.radius,
      innerWidth,
      innerHeight,
      height: innerHeight + 2 * padY + 2 * border,
      scale: innerWidth / capture.width,
      origin: [spec.left + border + padX, spec.top + border + padY],
    };
  }
  const border = spec.width * IPHONE.border;
  const innerWidth = spec.width - 2 * border;
  const innerHeight = (innerWidth * capture.height) / capture.width;
  return {
    border,
    padX: 0,
    padY: 0,
    radius: spec.width * IPHONE.radius,
    innerWidth,
    innerHeight,
    height: innerHeight + 2 * border,
    scale: innerWidth / capture.width,
    origin: [spec.left + border, spec.top + border],
  };
}

export function macWindowRadius(spec: FrameSpec): number {
  return spec.width * MAC.windowRadius;
}

/** Composition pixel of a capture pixel inside the given frame. */
export function capturePoint(spec: FrameSpec, point: Point): Point {
  const metrics = frameMetrics(spec);
  return [metrics.origin[0] + point[0] * metrics.scale, metrics.origin[1] + point[1] * metrics.scale];
}

export function lerpFrame(a: FrameSpec, b: FrameSpec, t: number): FrameSpec {
  const mix = (x: number, y: number) => x + (y - x) * t;
  return { device: a.device, left: mix(a.left, b.left), top: mix(a.top, b.top), width: mix(a.width, b.width) };
}

export type Layout = { readonly mac?: FrameSpec; readonly iphone?: FrameSpec };

export const LAYOUTS = {
  macSolo: { mac: { device: "mac", left: 250, top: 70, width: 1100 } },
  macWithPhone: {
    mac: { device: "mac", left: 60, top: 130, width: 1060 },
    iphone: { device: "iphone", left: 1190, top: 150, width: 340 },
  },
  phoneLead: {
    mac: { device: "mac", left: 90, top: 190, width: 900 },
    iphone: { device: "iphone", left: 1080, top: 62, width: 420 },
  },
  stepStill: {
    mac: { device: "mac", left: 60, top: 48, width: 1300 },
    iphone: { device: "iphone", left: 1440, top: 157, width: 420 },
  },
} as const satisfies Record<string, Layout>;

export type LayoutId = keyof typeof LAYOUTS;

export function lerpLayout(a: Layout, b: Layout, t: number): Layout {
  return {
    mac: a.mac && b.mac ? lerpFrame(a.mac, b.mac, t) : a.mac ?? b.mac,
    iphone: a.iphone && b.iphone ? lerpFrame(a.iphone, b.iphone, t) : a.iphone ?? b.iphone,
  };
}

package qupath.ext.polylinewand.engine.field;

import qupath.ext.polylinewand.BrushMode;
import qupath.ext.polylinewand.EndpointSide;
import qupath.ext.polylinewand.PolylineWandParameters;
import qupath.ext.polylinewand.StrokeContext;
import qupath.ext.polylinewand.engine.BrushEngine;
import qupath.lib.geom.Point2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Engine C. Arc-length parameterized displacement field over a locked
 * active window of vertices.
 */
public final class DisplacementFieldEngine implements BrushEngine {

    private WorkingCurve curve;
    private ActiveRange range;
    private final AtomicReference<List<Point2>> previewRef = new AtomicReference<>(List.of());
    private volatile boolean dirty = false;
    private EndpointSide eraseEnd = EndpointSide.NONE;
    private double brushRadius;

    @Override
    public void beginStroke(StrokeContext ctx, Point2 imgPt) {
        List<Point2> body = ctx.getRegion().body;
        if (body.size() < 2) {
            curve = new WorkingCurve(List.of(imgPt, imgPt));
        } else {
            curve = new WorkingCurve(body);
        }
        brushRadius = ctx.getBrushRadius();
        range = ActiveRange.compute(curve, imgPt, 2.0 * brushRadius);
        eraseEnd = decideEraseEnd(ctx, imgPt);
        publishPreview();
    }

    private EndpointSide decideEraseEnd(StrokeContext ctx, Point2 imgPt) {
        if (ctx.getMode() != BrushMode.ERASE_FROM_END || curve.size() < 2) {
            return EndpointSide.NONE;
        }
        Point2 first = curve.get(0);
        Point2 last = curve.get(curve.size() - 1);
        return distance(first, imgPt) <= distance(last, imgPt) ? EndpointSide.START : EndpointSide.END;
    }

    @Override
    public void applyDrag(StrokeContext ctx, Point2 imgPt, long nowNs) {
        if (curve == null || curve.size() < 2 || range.endIdx < range.startIdx) {
            return;
        }
        switch (ctx.getMode()) {
            case ERASE_FROM_END:
                applyEraseFromEnd(imgPt);
                break;
            case SMOOTH:
                applySmooth(imgPt);
                break;
            case PUSH:
            case AUTO:
            default:
                applyPushDisplacement(ctx, imgPt);
                break;
        }
        curve.recomputeFromIndex(Math.max(0, range.startIdx));
        densifyActive(brushRadius);
        publishPreview();
    }

    private void applyPushDisplacement(StrokeContext ctx, Point2 brushCenter) {
        KernelType kt = PolylineWandParameters.getFieldKernelType();
        double sigmaFrac = PolylineWandParameters.getFieldKernelSigmaFraction();
        double strength = PolylineWandParameters.getFieldDisplacementStrength();
        double dampStrength = PolylineWandParameters.getFieldVelocityDampingStrength();
        double dampMin = PolylineWandParameters.getFieldVelocityDampingMin();
        boolean guard = PolylineWandParameters.getFieldSelfIntersectionGuard();

        double[] vUnit = ctx.getVelocity().unitDirection();

        for (int i = range.startIdx; i <= range.endIdx; i++) {
            Point2 v = curve.get(i);
            double dx = v.getX() - brushCenter.getX();
            double dy = v.getY() - brushCenter.getY();
            double dist = Math.hypot(dx, dy);
            double w = DisplacementKernel.dispatch(kt, dist, brushRadius, sigmaFrac);
            if (w <= 0) {
                continue;
            }
            double nx = curve.normalX(i);
            double ny = curve.normalY(i);
            // Displacement is along the normal, in the direction that moves vertex AWAY from brush center
            // (push). Sign is determined by sign of dot(d, n).
            double dotDN = dx * nx + dy * ny;
            double sign = dotDN >= 0 ? 1.0 : -1.0;
            // Magnitude scales with how deep the vertex is inside the brush.
            double depth = brushRadius - dist;
            // Velocity damping by perpendicular motion magnitude.
            double damping = 1.0;
            if (dampStrength > 0 && (vUnit[0] != 0 || vUnit[1] != 0)) {
                double absDot = Math.abs(vUnit[0] * nx + vUnit[1] * ny);
                damping = Math.max(dampMin, 1.0 - dampStrength * absDot);
            }
            double mag = w * strength * depth * damping;
            Point2 candidate = new Point2(v.getX() + sign * nx * mag,
                                          v.getY() + sign * ny * mag);
            if (guard && SelfIntersectionGuard.wouldSelfIntersect(curve,
                    range.startIdx, range.endIdx, i, candidate)) {
                continue;
            }
            curve.setPoint(i, candidate);
        }
        dirty = true;
    }

    private void applySmooth(Point2 brushCenter) {
        for (int i = range.startIdx + 1; i <= range.endIdx - 1; i++) {
            Point2 prev = curve.get(i - 1);
            Point2 next = curve.get(i + 1);
            Point2 v = curve.get(i);
            double dx = v.getX() - brushCenter.getX();
            double dy = v.getY() - brushCenter.getY();
            double w = DisplacementKernel.dispatch(
                    PolylineWandParameters.getFieldKernelType(),
                    Math.hypot(dx, dy), brushRadius,
                    PolylineWandParameters.getFieldKernelSigmaFraction());
            if (w <= 0) {
                continue;
            }
            double midX = 0.5 * (prev.getX() + next.getX());
            double midY = 0.5 * (prev.getY() + next.getY());
            curve.setPoint(i, new Point2(
                    v.getX() + (midX - v.getX()) * w * 0.5,
                    v.getY() + (midY - v.getY()) * w * 0.5));
        }
        dirty = true;
    }

    private void applyEraseFromEnd(Point2 brushCenter) {
        if (curve.size() <= 2 || eraseEnd == EndpointSide.NONE) {
            return;
        }
        if (eraseEnd == EndpointSide.START) {
            int removeUpTo = -1;
            int n = curve.size();
            for (int i = 0; i < n; i++) {
                if (distance(curve.get(i), brushCenter) <= brushRadius) {
                    removeUpTo = i;
                } else {
                    break;
                }
            }
            int maxRemovable = Math.max(0, n - 2);
            removeUpTo = Math.min(removeUpTo, maxRemovable - 1);
            if (removeUpTo >= 0) {
                ArrayList<Point2> remaining = new ArrayList<>(n - (removeUpTo + 1));
                for (int i = removeUpTo + 1; i < n; i++) {
                    remaining.add(curve.get(i));
                }
                curve = new WorkingCurve(remaining);
                range = ActiveRange.wholeCurve(curve);
                dirty = true;
            }
        } else {
            int n = curve.size();
            int removeFrom = n;
            for (int i = n - 1; i >= 0; i--) {
                if (distance(curve.get(i), brushCenter) <= brushRadius) {
                    removeFrom = i;
                } else {
                    break;
                }
            }
            int minIndex = n - Math.max(0, n - 2);
            if (removeFrom < n && removeFrom >= minIndex) {
                ArrayList<Point2> remaining = new ArrayList<>(removeFrom);
                for (int i = 0; i < removeFrom; i++) {
                    remaining.add(curve.get(i));
                }
                curve = new WorkingCurve(remaining);
                range = ActiveRange.wholeCurve(curve);
                dirty = true;
            }
        }
    }

    private void densifyActive(double radius) {
        double divisor = Math.max(1.0, PolylineWandParameters.getFieldDensifyDivisor());
        double maxSeg = radius / divisor;
        // Walk active range in reverse so insert indices stay valid.
        for (int i = range.endIdx - 1; i >= range.startIdx; i--) {
            Point2 a = curve.get(i);
            Point2 b = curve.get(i + 1);
            double seg = Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
            if (seg <= maxSeg) {
                continue;
            }
            Point2 mid = new Point2(0.5 * (a.getX() + b.getX()), 0.5 * (a.getY() + b.getY()));
            curve.insertPoint(i + 1, mid);
            range.endIdx++;
        }
    }

    @Override
    public List<Point2> endStroke(StrokeContext ctx) {
        return curve == null || curve.size() < 2 ? List.of() : curve.snapshot();
    }

    @Override
    public void cancel(StrokeContext ctx) { }

    @Override
    public List<Point2> previewSnapshot() {
        return previewRef.get();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        dirty = false;
    }

    private void publishPreview() {
        previewRef.set(new ArrayList<>(curve.view()));
    }

    private static double distance(Point2 a, Point2 b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }
}

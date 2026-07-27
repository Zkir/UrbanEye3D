import math

def generate_tower():
    vertices = []
    faces = []

    def add_vertex(x, y, z):
        vertices.append((x, y, z))
        return len(vertices)

    def add_quad_outward(p1, p2, p3, p4):
        # Create vertices and ensure normal points away from center
        v_idx = [add_vertex(*p1), add_vertex(*p2), add_vertex(*p3), add_vertex(*p4)]
        
        cx = (p1[0] + p2[0] + p3[0] + p4[0]) / 4.0
        cz = (p1[2] + p2[2] + p3[2] + p4[2]) / 4.0
        
        ax, ay, az = p2[0]-p1[0], p2[1]-p1[1], p2[2]-p1[2]
        bx, by, bz = p3[0]-p1[0], p3[1]-p1[1], p3[2]-p1[2]
        nx = ay*bz - az*by
        ny = az*bx - ax*bz
        nz = ax*by - ay*bx
        
        if nx*cx + nz*cz < 0:
            faces.append((v_idx[0], v_idx[3], v_idx[2], v_idx[1]))
        else:
            faces.append(tuple(v_idx))

    def add_vec(a, b):
        return (a[0]+b[0], a[1]+b[1], a[2]+b[2])

    def get_flange_vec(pa, pb, pref, t):
        # Calculate flange vector perpendicular to beam (pa-pb) and in plane of pref
        d = (pb[0]-pa[0], pb[1]-pa[1], pb[2]-pa[2])
        r = (pref[0]-pa[0], pref[1]-pa[1], pref[2]-pa[2])
        # Plane normal
        nx = d[1]*r[2] - d[2]*r[1]
        ny = d[2]*r[0] - d[0]*r[2]
        nz = d[0]*r[1] - d[1]*r[0]
        # Vector in plane perp to d
        wx = ny*d[2] - nz*d[1]
        wy = nz*d[0] - nx*d[2]
        wz = nx*d[1] - ny*d[0]
        wl = math.sqrt(wx*wx + wy*wy + wz*wz)
        if wl < 0.0001: return (0,0,0)
        wx, wy, wz = wx/wl*t, wy/wl*t, wz/wl*t
        # Ensure it points towards pref (inward)
        if wx*r[0] + wy*r[1] + wz*r[2] < 0:
            wx, wy, wz = -wx, -wy, -wz
        return (wx, wy, wz)

    def get_bisector_offset(p_center, pA, pB, t):
        # Calculate offset at a junction using bisector and tapering correction
        dA = (pA[0]-p_center[0], pA[1]-p_center[1], pA[2]-p_center[2])
        dB = (pB[0]-p_center[0], pB[1]-p_center[1], pB[2]-p_center[2])
        la = math.sqrt(dA[0]**2 + dA[1]**2 + dA[2]**2)
        lb = math.sqrt(dB[0]**2 + dB[1]**2 + dB[2]**2)
        if la < 0.001 or lb < 0.001: return (0,0,0)
        da = (dA[0]/la, dA[1]/la, dA[2]/la)
        db = (dB[0]/lb, dB[1]/lb, dB[2]/lb)
        # Bisector
        b = (da[0]+db[0], da[1]+db[1], da[2]+db[2])
        lb_mag = math.sqrt(b[0]**2 + b[1]**2 + b[2]**2)
        if lb_mag < 0.001: return (0,0,0)
        b = (b[0]/lb_mag, b[1]/lb_mag, b[2]/lb_mag)
        # sin(alpha/2) via cross product
        cx = b[1]*da[2] - b[2]*da[1]
        cy = b[2]*da[0] - b[0]*da[2]
        cz = b[0]*da[1] - b[1]*da[0]
        sin_half = math.sqrt(cx**2 + cy**2 + cz**2)
        if sin_half < 0.001: return (0,0,0)
        s = t / sin_half
        return (b[0]*s, b[1]*s, b[2]*s)

    levels = [
        (0, 2.5), (9, 1.2), (13, 1.0), (15, 1.0), (19, 0.8), (21, 0.8), (25, 0.0)
    ]

    def get_post_pos(lvl_idx, side, h_override=None):
        h, w = levels[lvl_idx]
        if h_override is not None:
            for k in range(len(levels)-1):
                h_low, w_low = levels[k]
                h_high, w_high = levels[k+1]
                if h_low <= h_override <= h_high:
                    t = (h_override - h_low) / (h_high - h_low)
                    w = w_low + (w_high - w_low) * t
                    h = h_override
                    break
        if side == 0: return (w, h, w)
        if side == 1: return (w, h, -w)
        if side == 2: return (-w, h, -w)
        if side == 3: return (-w, h, w)
        return (0,0,0)

    # 1. Posts
    top_sq = 0.15
    tsq = top_sq / 2.0
    top_pts = [(tsq, 25.0, tsq), (tsq, 25.0, -tsq), (-tsq, 25.0, -tsq), (-tsq, 25.0, tsq)]

    for side in range(4):
        for i in range(len(levels) - 1):
            p1 = get_post_pos(i, side)
            p2 = get_post_pos(i+1, side)
            
            p1_prev = get_post_pos(0, (side+3)%4, p1[1])
            p1_next = get_post_pos(0, (side+1)%4, p1[1])
            
            if i == len(levels) - 2:
                # Peak section: converge to the top square opening
                p2 = top_pts[side]
                p2_prev = top_pts[(side+3)%4]
                p2_next = top_pts[(side+1)%4]
            else:
                p2_prev = get_post_pos(0, (side+3)%4, p2[1])
                p2_next = get_post_pos(0, (side+1)%4, p2[1])

            t = 0.15
            w1_1 = get_flange_vec(p1, p2, p1_prev, t)
            w1_2 = get_flange_vec(p1, p2, p1_next, t)
            w2_1 = get_flange_vec(p2, p1, p2_prev, t)
            w2_2 = get_flange_vec(p2, p1, p2_next, t)
            
            v1_w1, v1_w2 = add_vec(p1, w1_1), add_vec(p1, w1_2)
            v2_w1, v2_w2 = add_vec(p2, w2_1), add_vec(p2, w2_2)

            add_quad_outward(p1, p2, v2_w1, v1_w1)
            add_quad_outward(p1, v1_w2, v2_w2, p2)

    # 2. Lattice
    thickness = 0.15
    post_t = 0.15 
    flicker_offset = 0.01 
    for side in range(4):
        p_a_ref, p_b_ref = get_post_pos(0, side), get_post_pos(0, (side+1)%4)
        fx, fz = p_b_ref[0]-p_a_ref[0], p_b_ref[2]-p_a_ref[2]
        fl = math.sqrt(fx*fx + fz*fz)
        ux_f, uz_f = fx/fl, fz/fl
        inx, inz = uz_f * flicker_offset, -ux_f * flicker_offset

        def get_inner(s, h):
            p = get_post_pos(0, s, h)
            shift_sign = 1.0 if s == side else -1.0
            return (p[0] + inx + ux_f * post_t * shift_sign, 
                    p[1], 
                    p[2] + inz + uz_f * post_t * shift_sign)

        for i in range(len(levels) - 2):
            h1, h2 = levels[i][0], levels[i+1][0]
            if i == 0: h1 = 2.0

            # Calculate actual width at current height to account for tapering
            p1_a, p1_b = get_inner(side, h1), get_inner((side+1)%4, h1)
            cur_dist_horiz = math.sqrt((p1_b[0]-p1_a[0])**2 + (p1_b[2]-p1_a[2])**2)

            dy = h2 - h1
            dist = math.sqrt(cur_dist_horiz**2 + dy**2)
            # t_v is the vertical projection needed to maintain 'thickness' perpendicular to the beam
            t_v = thickness * dist / cur_dist_horiz if cur_dist_horiz > 0.001 else thickness

            # Horizontal (angle is 0, so vertical projection == thickness)
            add_quad_outward(get_inner(side, h1), get_inner((side+1)%4, h1), 
                             get_inner((side+1)%4, h1 + thickness), get_inner(side, h1 + thickness))
            # Diagonal UP
            add_quad_outward(get_inner(side, h1), get_inner((side+1)%4, h2-t_v),
                             get_inner((side+1)%4, h2), get_inner(side, h1+t_v))
            # Diagonal DOWN
            add_quad_outward(get_inner(side, h2-t_v), get_inner((side+1)%4, h1),
                             get_inner((side+1)%4, h1+t_v), get_inner(side, h2))

        h_peak_base = levels[-2][0]
        add_quad_outward(get_inner(side, h_peak_base), get_inner((side+1)%4, h_peak_base),
                         get_inner((side+1)%4, h_peak_base + thickness), get_inner(side, h_peak_base + thickness))

    # 3. Arms
    arm_sections = [(2, 3, 6.0), (4, 5, 4.5)]
    arm_tip_size = 0.2
    for l_bot, l_top, w_arm in arm_sections:
        h_bot, w_trunk_bot = levels[l_bot]
        h_top, w_trunk_top = levels[l_top]
        for x_sign in [1, -1]:
            # Trunk attachment points
            p_tf = (x_sign*w_trunk_top, h_top, w_trunk_top)
            p_tb = (x_sign*w_trunk_top, h_top, -w_trunk_top)
            p_bf = (x_sign*w_trunk_bot, h_bot, w_trunk_bot)
            p_bb = (x_sign*w_trunk_bot, h_bot, -w_trunk_bot)
            
            # Tip square vertices
            y_mid = (h_bot+h_top)/2.0
            ts = arm_tip_size / 2.0
            p_tip_tf = (x_sign*w_arm, y_mid + ts,  ts)
            p_tip_tb = (x_sign*w_arm, y_mid + ts, -ts)
            p_tip_bf = (x_sign*w_arm, y_mid - ts,  ts)
            p_tip_bb = (x_sign*w_arm, y_mid - ts, -ts)
            
            t = 0.15

            def add_arm_beam(pa, pb, pref_a1, pref_a2, pref_b1, pref_b2):
                # Calculate flanges at both ends to ensure correct orientation
                w1_a = get_flange_vec(pa, pb, pref_a1, t)
                w2_a = get_flange_vec(pa, pb, pref_a2, t)
                w1_b = get_flange_vec(pb, pa, pref_b1, t)
                w2_b = get_flange_vec(pb, pa, pref_b2, t)
                
                v_a1, v_a2 = add_vec(pa, w1_a), add_vec(pa, w2_a)
                v_b1, v_b2 = add_vec(pb, w1_b), add_vec(pb, w2_b)
                
                add_quad_outward(pa, pb, v_b1, v_a1)
                add_quad_outward(pa, v_a2, v_b2, pb)

            # Top Front beam
            add_arm_beam(p_tf, p_tip_tf, p_tb, p_bf, p_tip_tb, p_tip_bf)
            # Top Back beam
            add_arm_beam(p_tb, p_tip_tb, p_tf, p_bb, p_tip_tf, p_tip_bb)
            # Bottom Front beam
            add_arm_beam(p_bf, p_tip_bf, p_bb, p_tf, p_tip_bb, p_tip_tf)
            # Bottom Back beam
            add_arm_beam(p_bb, p_tip_bb, p_bf, p_tb, p_tip_bf, p_tip_tb)
            
    with open("src/main/resources/models/power_tower.obj", "w") as f:
        f.write("# Pro-Lattice Power Tower - Procedurally Correct Geometry\n")
        for v in vertices: f.write(f"v {v[0]:.3f} {v[1]:.3f} {v[2]:.3f}\n")
        for face in faces: f.write("f " + " ".join(map(str, face)) + "\n")

    generate_billboard(vertices, faces)

def generate_billboard(vertices, faces):
    from PIL import Image, ImageDraw
    
    # 1. Image settings
    # Tower is 25m high, max width is 12m (arm width 6.0 * 2)
    w_m, h_m = 12.5, 25.5
    scale = 80 # pixels per meter
    img_w, img_h = int(w_m * scale), int(h_m * scale)
    
    img = Image.new("RGBA", (img_w, img_h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    def to_px(x, y):
        px = (x + w_m/2.0) * scale
        py = (h_m - y) * scale # Flip Y for image coordinates
        return (px, py)

    # 2. Draw faces (sorted by Z to handle simple occlusion if needed, 
    # but for a lattice it's mostly fine to just draw all)
    # Using front view (X, Y)
    for face in faces:
        poly = []
        for v_idx in face:
            v = vertices[v_idx-1]
            poly.append(to_px(v[0], v[1]))
        
        # Draw with a slight outline for better visibility of thin beams
        draw.polygon(poly, fill=(100, 105, 110, 255), outline=(70, 75, 80, 255))

    img.save("src/main/resources/models/power_tower_billboard.png")
    print(f"✅ Billboard texture saved to src/main/resources/models/power_tower_billboard.png")

    # 3. Generate Billboard OBJ
    # A simple quad facing Z+
    # Width and height should match the texture proportions exactly
    hw, hh = w_m / 2.0, h_m
    obj_lines = [
        "# Power Tower Billboard",
        "mtllib power_tower_billboard.mtl",
        f"v {-hw:.3f} 0.000 0.000",
        f"v {hw:.3f} 0.000 0.000",
        f"v {hw:.3f} {hh:.3f} 0.000",
        f"v {-hw:.3f} {hh:.3f} 0.000",
        "vt 0.000 0.000",
        "vt 1.000 0.000",
        "vt 1.000 1.000",
        "vt 0.000 1.000",
        "usemtl billboard_mat",
        "f 1/1 2/2 3/3 4/4"
    ]
    
    with open("src/main/resources/models/power_tower_billboard.obj", "w") as f:
        f.write("\n".join(obj_lines) + "\n")
    print(f"✅ Billboard model saved to src/main/resources/models/power_tower_billboard.obj")

    # 4. Generate Material File
    mtl_lines = [
        "newmtl billboard_mat",
        "Kd 1.0 1.0 1.0",
        "map_Kd power_tower_billboard.png",
        "d 1.0"
    ]
    with open("src/main/resources/models/power_tower_billboard.mtl", "w") as f:
        f.write("\n".join(mtl_lines) + "\n")

if __name__ == "__main__":
    generate_tower()

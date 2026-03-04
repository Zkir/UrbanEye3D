package ru.zkir.urbaneye3d.roofgenerators.linearprofile;

import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MesherLinearProfileQuasiRectangular {
    static final double ZERO = 1e-6;
    protected RenderableElement building;
    public List<Point3D> verts = new ArrayList<>();
    Polygon polygon;
    List<Double> projections = new ArrayList<>();
    double roofHeight;
    double polygonWidth;
    int minProjIndex;
    int maxProjIndex;
    Point3D direction;
    boolean noWalls;
    double roofVerticalPosition;
    public List<List<Integer>> roofIndices = new ArrayList<>();
    public List<List<Integer>> wallIndices = new ArrayList<>();
    public List<List<Integer>> bottomFaces = new ArrayList<>();

    Point2D[] profile;
    int numSlots;
    int lastProfileIndex;
    int numSamples;
    double angleToHeight;
    boolean lEndZero;
    boolean rEndZero;
    int[] profileQ;
    Slot[] slots;
    Slot slot;
    Slot originSlot;

    private static void debugMsg(String s){
        //System.err.println(s);
    }

    public String toString() {
        String s = "";
        s += "verts " + this.verts;
        s += " polygon.indices " + this.polygon.indices;
        s += " projections " + this.projections;
        s += " profile " + this.profile;
        s += " direction " + this.direction;
        s += " lEndZero " + this.lEndZero;
        s += " rEndZero " + this.rEndZero;
        return s;
    }


    public MesherLinearProfileQuasiRectangular(LinearProfiles profile_data) {
        debugMsg("\nDEBUG: RoofProfile constructor entry");
        debugMsg("    Params: data=" + profile_data);

        this.profile = profile_data.profile;

        this.numSamples = profile_data.numSamples;
        this.angleToHeight = profile_data.angleToHeight;

        numSlots = profile.length;
        lastProfileIndex = numSlots - 1;
        slots = new Slot[numSlots];
        for (int i = 0; i < numSlots; i++) {
            slots[i] = new Slot(profile[i].x);
        }
        for (int i = 0; i < lastProfileIndex; i++) {
            slots[i].n = slots[i + 1];
        }

        lEndZero = Math.abs(profile[0].y) < ZERO;
        rEndZero = Math.abs(profile[lastProfileIndex].y) < ZERO;

        int[] _profile = new int[numSlots];
        for (int i = 0; i < numSlots; i++) {
            _profile[i] = (int) Math.ceil(profile[i].x * numSamples);
        }

        profileQ = new int[numSamples + 1];
        int idx = 0;
        for (int i = 0; i < numSamples; i++) {
            if (i >= _profile[idx + 1]) {
                idx++;
            }
            profileQ[i] = idx;
        }
        profileQ[numSamples] = idx;
    }


    public void init(RenderableElement building) {
        debugMsg("\nDEBUG: RoofProfile.init() entry");

        this.building = building;
        this.noWalls = (building.wallHeight <= building.minHeight);

        polygon = new Polygon(building.getContour());

        //filling vertices
        int n = building.getContour().size();
        for (int i = 0; i < n; i++) {
            var p2d = building.getContour().get(n - i - 1);
            verts.add(new Point3D(p2d.x, p2d.y, building.minHeight));
        }
        roofVerticalPosition = building.wallHeight;
        roofHeight = building.roofHeight;

        initProfile();
    }


    void initProfile() {
        debugMsg("\nDEBUG: RoofProfile.initProfile() entry");
        for (int i = 0; i < lastProfileIndex; i++) {
            slots[i].reset();
        }
    }


    public boolean make() {
        debugMsg("\nDEBUG: RoofProfile.make() entry");
        debugMsg("    Params: ");
        if (projections.isEmpty()) {
            processDirection();
        }

        debugMsg("        slots:  ");
        for (var s : this.slots) {
            debugMsg("            " + s.toString());
        }

        debugMsg("        polygon: " + polygon.toString().replace("[", "(").replace("]", ")"));
        debugMsg("        polygonWidth: " + polygonWidth);
        debugMsg("        verts:  " + verts.toString());
        debugMsg("        roofIndices: " + roofIndices.toString());
        debugMsg("        projections: " + projections.toString());
        debugMsg("        roofHeight: " + roofHeight);
        debugMsg("        minProjIndex: " + minProjIndex);
        debugMsg("        direction: " + direction);
        debugMsg("        roofVerticalPosition: " + roofVerticalPosition);

        boolean noWalls = this.noWalls;
        slot = slots[0];
        originSlot = slots[0];

        int i0 = minProjIndex;
        int i = i0;
        ProfiledVert pv1 = getProfiledVert(i, roofVerticalPosition, noWalls);
        ProfiledVert pv0 = pv1;
        ProfiledVert _pv = null;

        do {
            i = polygon.next(i);
            if (i == i0) break;
            ProfiledVert pv2 = getProfiledVert(i, roofVerticalPosition, noWalls);
            createProfileVertices(pv1, pv2, _pv);
            _pv = pv1;
            pv1 = pv2;
        } while (i != i0);

        createProfileVertices(pv1, pv0, _pv);

        // Перенос вершин из первого слота во второй
        Slot firstSlot = slots[0];
        Slot secondSlot = slots[1];
        if (!firstSlot.parts.isEmpty() && !secondSlot.parts.isEmpty()) {
            List<Integer> firstPart = firstSlot.parts.get(0).vertIndices;
            List<Integer> lastPart = secondSlot.parts.get(secondSlot.parts.size() - 1).vertIndices;
            for (int j = 1; j < firstPart.size(); j++) {
                lastPart.add(firstPart.get(j));
            }
            secondSlot.endAtSelf.add(true);
        }

        // Подготовка слотов
        for (int j = 1; j < lastProfileIndex; j++) {
            slots[j].prepare();
        }

        // Формирование граней крыши
        Slot slotR = slots[1];
        slotR.trackUp(roofIndices, null, null);

        for (int j = 1; j < lastProfileIndex; j++) {
            Slot slotL = slotR;
            slotR = slots[j + 1];
            slotR.trackUp(roofIndices, null, null);
            slotL.trackDown(roofIndices, null, null);
        }

        makeBottom();

        /*
        MesherLinearProfileQuasiRectangular.debugMsg("DEBUG: MAKE COMPLETED!!!");

        String verts_str="";
        Point3D vert0 = mesh.verts.get(0);
        for(var vert:mesh.verts){
            verts_str += "\n        (" +
                    String.format(Locale.ROOT, "%.4f", vert.x-vert0.x) +", " +
                    String.format(Locale.ROOT, "%.4f", vert.y-vert0.y) +", " +
                    String.format(Locale.ROOT, "%.4f", vert.z-vert0.z) +") ";
        }

        MesherLinearProfileQuasiRectangular.debugMsg("verts="+verts_str);

        String roofIndices_str="[";
        String wallIndices_str="[";
        for (var face:mesh.roofFaces) {
            if (roofIndices_str.length()!=1) {roofIndices_str +=", ";}
            roofIndices_str += Arrays.toString(face);
        }

        roofIndices_str+="]";

        for (var face:mesh.wallFaces) {
            if (wallIndices_str.length()!=1) {wallIndices_str +=", ";}
            wallIndices_str += (Arrays.toString(face));
        }
        wallIndices_str+="]";

        MesherLinearProfileQuasiRectangular.debugMsg("roofIndices"+roofIndices_str);
        MesherLinearProfileQuasiRectangular.debugMsg("wallIndices"+wallIndices_str);
        */


        return true;
    }


    ProfiledVert getProfiledVert(int i, double roofVerticalPosition, boolean noWalls) {
        debugMsg("\nDEBUG: RoofProfile.getProfiledVert() entry");
        debugMsg("    Params: i=" + i + ", roofVerticalPosition=" +
                roofVerticalPosition + ", noWalls=" + noWalls);

        return new ProfiledVert(this, i, roofVerticalPosition, noWalls);
    }


    void createProfileVertices(ProfiledVert pv1, ProfiledVert pv2, ProfiledVert _pv) {
        debugMsg("\nDEBUG: RoofProfile.createProfileVertices() entry");
        debugMsg("    Params: pv1=" + pv1 + ", pv2=" + pv2 + ", _pv=" + _pv);

        List<Point3D> verts = this.verts;
        List<Integer> indices = polygon.indices;
        Point2D[] p = profile;
        List<List<Integer>> wallIndices = this.wallIndices;
        Slot[] slots = this.slots;
        Slot slot = this.slot;

        int index1 = pv1.index;
        int index2 = pv2.index;
        boolean skip1 = noWalls && pv1.onSlot &&
                ((lEndZero && index1 == 0) ||
                        (rEndZero && index1 == lastProfileIndex));
        boolean skip2 = noWalls && pv2.onSlot &&
                ((lEndZero && index2 == 0) ||
                        (rEndZero && index2 == lastProfileIndex));

        if (skip1 && skip2 && index1 == index2) {
            if (_pv == null) {
                slot.append(pv1.vertIndex, pv1.y, originSlot, null);
            }
            slot.append(pv2.vertIndex);
            return;
        }

        List<Integer> _wallIndices = new ArrayList<>();
        boolean appendPv1 = true;
        if (skip1) {
            _wallIndices.add(pv1.vertIndex);
            appendPv1 = false;
        } else {
            _wallIndices.add(indices.get(pv1.i));
        }
        if (!skip2) {
            _wallIndices.add(indices.get(pv2.i));
        }
        _wallIndices.add(pv2.vertIndex);

        Point3D v1 = verts.get(indices.get(pv1.i));
        Point3D v2 = verts.get(indices.get(pv2.i));
        Point3D _v = _pv != null ? verts.get(indices.get(_pv.i)) : null;

        if (_pv == null) {
            slot.append(pv1.vertIndex, pv1.y, originSlot, null);
        } else if (pv1.onSlot) {
            Boolean reflection = null;
            boolean appendToSlot = false;

            if (pv2.onSlot && index1 == index2) {
                if ((index1 != lastProfileIndex && _pv.x < pv1.x && pv1.y > pv2.y) ||
                        (index1 != 0 && _pv.x > pv1.x && pv1.y < pv2.y)) {
                    appendToSlot = true;
                }
            } else if (pv1.x < pv2.x) {
                if (_pv.x < pv1.x) {
                    appendToSlot = true;
                } else if (index1 != 0) {
                    if (_pv.onSlot && _pv.index == index1) {
                        if (_pv.y < pv1.y) {
                            appendToSlot = true;
                        }
                    } else {
                        double cross = (pv2.x - pv1.x) * (_pv.y - pv1.y) -
                                (pv2.y - pv1.y) * (_pv.x - pv1.x);
                        if (cross < 0) {
                            appendToSlot = true;
                            reflection = true;
                        }
                    }
                }
            } else {
                if (_pv.x > pv1.x) {
                    appendToSlot = true;
                } else if (index1 != lastProfileIndex) {
                    if (_pv.onSlot && _pv.index == index1) {
                        if (_pv.y > pv1.y) {
                            appendToSlot = true;
                        }
                    } else {
                        double cross = (pv2.x - pv1.x) * (_pv.y - pv1.y) -
                                (pv2.y - pv1.y) * (_pv.x - pv1.x);
                        if (cross < 0) {
                            appendToSlot = true;
                            reflection = false;
                        }
                    }
                }
            }

            if (appendToSlot) {
                originSlot = slot;
                slot = slots[index1];
                slot.append(pv1.vertIndex, pv1.y, originSlot, reflection);
            }
        }

        if (index1 != index2) {
            if (index2 > index1) {
                if (!pv2.onSlot || index1 != index2 - 1) {
                    int start = pv2.onSlot ? index2 - 1 : index2;
                    slot = createVerticesBetween(slot, pv1, pv2, v1, v2,
                            start, index1, -1, _wallIndices);
                }
            } else {
                if (!pv1.onSlot || index2 != index1 - 1) {
                    int start = pv1.onSlot ? index1 : index1 + 1;
                    slot = createVerticesBetween(slot, pv1, pv2, v1, v2,
                            index2 + 1, start, 1, _wallIndices);
                }
            }
        }

        if (appendPv1) {
            _wallIndices.add(pv1.vertIndex);
        }
        wallIndices.add(_wallIndices);
        slot.append(pv2.vertIndex);
        this.slot = slot;
    }


    Slot createVerticesBetween(Slot slot, ProfiledVert pv1, ProfiledVert pv2,
                                                   Point3D v1, Point3D v2, int start, int end,
                                                   int step, List<Integer> _wallIndices) {

        debugMsg("\nDEBUG: common_code() entry");
        debugMsg("    Params:  " + slot);


        List<Point3D> verts = this.verts;
        Point2D[] p = profile;
        Slot[] slots = this.slots;

        int vertIndex = verts.size();
        int count = Math.abs(start - end);
        int vertIndexForSlots = vertIndex + count - 1;

        double factorX = (v2.x - v1.x) / (pv2.x - pv1.x);
        double factorY = (v2.y - v1.y) / (pv2.x - pv1.x);
        double factorSlots = (pv2.y - pv1.y) / (pv2.x - pv1.x);
        debugMsg("        qq=" + start + " " + end + " " + step);


        int slotIndexVerts = start;
        int slotIndex = end - step;
        for (int i = 0; i < count; i++) {
            double factor = p[slotIndexVerts].x - pv1.x;
            double x = v1.x + factor * factorX;
            double y = v1.y + factor * factorY;
            double z = roofVerticalPosition + roofHeight * p[slotIndexVerts].y;
            debugMsg("        z=" + z);
            verts.add(new Point3D(x, y, z));
            debugMsg("        vertIndex:" + vertIndex);
            _wallIndices.add(vertIndex);

            slot.append(vertIndexForSlots);
            originSlot = slot;
            slot = slots[slotIndex];
            debugMsg("        slotIndex=" + slotIndex);
            debugMsg("        " + pv1.y + " " + factorSlots + " " + p[slotIndex].x + " " + pv1.x);
            double yCoord = pv1.y + factorSlots * (p[slotIndex].x - pv1.x);

            slot.append(vertIndexForSlots, yCoord, originSlot, null);
            slot.processWallFace(_wallIndices, pv1, pv2);

            vertIndex++;
            vertIndexForSlots--;
            slotIndexVerts += step;
            slotIndex -= step;
        }
        debugMsg("\n     : common_code() exit");
        debugMsg("    Returns: " + slot);
        return slot;
    }


    void processDirection() {

        // <d> stands for direction
        // this actually differs from reference Blosm implementation
        // ridge direction can be either along or across
        // even if direction angle is specified, it only helps to choose orientation.

        var alongDirection = this.polygon.getDefaultDirection();
        var acrossDirection = this.polygon.getAcrossDirection();

        this.direction = alongDirection;// default value is "along"

        if (!building.roofOrientation.isEmpty()) {
            if ("across".equals(building.roofOrientation)) {
                // The roof ridge is across the longest side of the building outline,
                // i.e. the profile direction is along the longest side
                this.direction = acrossDirection;
            } else {
                this.direction = alongDirection;
            }
        } else if (building.roofDirection != null && !Double.isNaN(building.roofDirection)) {
            double d = Math.toRadians(building.roofDirection);
            var orig_direction = new Point3D(Math.sin(d), Math.cos(d), 0.);
            if (Math.abs(orig_direction.dot(alongDirection)) > Math.abs(orig_direction.dot(acrossDirection))) {
                this.direction = alongDirection;
            } else {
                this.direction = acrossDirection;
            }
        }

        // For each vertex from <polygon.verts> calculate projection of the vertex
        // on the vector <d> that defines the roof direction

        // Рассчитываем проекции вершин на вектор направления
        this.projections.clear();
        for (int i = 0; i < polygon.n; i++) {
            Point3D v = this.verts.get(polygon.indices.get(i));
            double projection = this.direction.x * v.x + this.direction.y * v.y;
            this.projections.add(projection);
        }

        // Находим индексы минимальной и максимальной проекции
        double minProj = Double.MAX_VALUE;
        double maxProj = Double.MIN_VALUE;
        int minIndex = 0;
        int maxIndex = 0;

        for (int i = 0; i < polygon.n; i++) {
            double proj = this.projections.get(i);
            if (proj < minProj) {
                minProj = proj;
                minIndex = i;
            }
            if (proj > maxProj) {
                maxProj = proj;
                maxIndex = i;
            }
        }
        this.minProjIndex = minIndex;
        this.maxProjIndex = maxIndex;
        this.polygonWidth = maxProj - minProj;
    }


    public void makeBottom() {
        ArrayList<Integer> bottomFace = new ArrayList<>();
        // We need to create faces with correct winding (counter-clockwise for bottom face when viewed from outside).
        int n = polygon.indices.size();
        for (int i = 0; i < n; i++) {
            bottomFace.add(polygon.indices.get(n - i - 1)); //in reverse order, to get proper normals
        }
        bottomFaces.add(bottomFace);
    }

    static class ProfiledVert {
        double x, y;
        int i;
        double h;
        int index;
        boolean onSlot;
        int vertIndex;
        MesherLinearProfileQuasiRectangular roof;

        public ProfiledVert(MesherLinearProfileQuasiRectangular roof, int i, double roofVerticalPosition, boolean noWalls) {
            debugMsg("\nDEBUG: ProfiledVert constructor entry");
            debugMsg("    Params: RoofProfile: " + roof + ", i=" + i +
                    ", roofVerticalPosition=" + roofVerticalPosition + ", noWalls=" + noWalls);
            this.roof = roof;
            this.i = i;
            List<Point3D> verts = roof.verts;
            List<Integer> indices = roof.polygon.indices;
            List<Double> proj = roof.projections;
            Point2D[] p = roof.profile;
            Point3D d = roof.direction;
            Point3D v = verts.get(indices.get(i));

            onSlot = false;
            boolean createVert = true;
            x = (proj.get(i) - proj.get(roof.minProjIndex)) / roof.polygonWidth;
            y = -v.x * d.y + v.y * d.x;

            int profileIndex = (int) Math.floor(x * roof.numSamples);
            int index = roof.profileQ[profileIndex];
            double distance = x - p[index].x;

            if (distance < ZERO) {
                onSlot = true;
                if (roof.lEndZero && noWalls && index == 0) {
                    createVert = false;
                    x = 0.0;
                    vertIndex = indices.get(i);
                } else {
                    x = p[index].x;
                    h = p[index].y;
                }
            } else if (Math.abs(p[index + 1].x - x) < ZERO) {
                onSlot = true;
                index++;
                if (roof.rEndZero && noWalls && index == roof.lastProfileIndex) {
                    createVert = false;
                    x = 1.0;
                    vertIndex = indices.get(i);
                } else {
                    x = p[index].x;
                    h = p[index].y;
                }
            } else {
                double h1 = p[index].y;
                double h2 = p[index + 1].y;
                h = h1 + (h2 - h1) / (p[index + 1].x - p[index].x) * distance;
            }

            if (createVert) {
                vertIndex = verts.size();
                double roofZ = roofVerticalPosition + roof.roofHeight * h;
                verts.add(new Point3D(v.x, v.y, roofZ));
                this.h = h;
            } else {
                this.h = 0.0;
            }

            this.index = index;
        }

        @Override
        public String toString() {
            return "ProfiledVert: x " + x + " y " + y + " i " + i + " h " + h +
                   " index " + index + " onSlot " + onSlot + " vertIndex " + vertIndex;
        }
    }

    static class Slot {
        double x;
        List<Part> parts = new ArrayList<>();
        List<List<Integer>> partsR = new ArrayList<>();
        List<Boolean> endAtSelf = new ArrayList<>();
        Slot n;
        int index;

        public Slot(double x) {
            debugMsg("\nDEBUG: Slot constructor entry ");
            debugMsg("    Params: x=" + x);
            this.x = x;
        }
        @Override
        public String toString() {
            return "Slot: x " + this.x + " parts " + this.parts + " partsR " + this.partsR + " endAtSelf  " + this.endAtSelf;
        }


        void reset() {
            debugMsg("\nDEBUG: Slot.reset() entry");
            debugMsg("    Params: ");
            parts.clear();
            partsR.clear();
            endAtSelf.clear();
            index = 0;
        }

        void prepare() {
            debugMsg("\nDEBUG: Slot.prepare() entry");
            debugMsg("    Self: " + this);

            parts.sort(Comparator.comparingDouble(p -> p.y));
        }

        void append(int vertIndex) {
            debugMsg("\nDEBUG: Slot.append() entry");
            debugMsg("    Params:  " + vertIndex+ " None None None");
            debugMsg("      Self:  " + this);
            if (!parts.isEmpty()) {
                Part lastPart = parts.get(parts.size() - 1);
                lastPart.vertIndices.add(vertIndex);
            }
        }

        void append(int vertIndex, double y, Slot originSlot, Boolean reflection) {
            debugMsg("\nDEBUG: Slot.append() entry");
            debugMsg("    Params:  " + vertIndex + " " + y +
                    " " + originSlot + " " + reflection);
            debugMsg("      Self:  " + this);
            List<Integer> vertIndices = new ArrayList<>();
            vertIndices.add(vertIndex);
            Part part = new Part(y, vertIndices, reflection, index);
            parts.add(part);
            originSlot.endAtSelf.add(originSlot == this);
            index++;
        }

        int trackDown(List<List<Integer>> roofIndices, Integer startIndex, Integer destVertIndex) {
            debugMsg("\nDEBUG: Slot.trackDown() entry");
            debugMsg("    Params: roofIndices=" + roofIndices);
            debugMsg("            startIndex=" + startIndex + ", destVertIndex=" + destVertIndex);
            //debugMsg("      Self:  " + this);
            List<Part> partsList = this.parts;
            int indexPartR = -1;
            int index = (startIndex == null ? partsList.size() : startIndex) - 2;
            Integer vertIndex0 = null;
            List<Integer> roofFace = null;

            while (index >= 0) {
                Part part = partsList.get(index);
                if (vertIndex0 == null) {
                    vertIndex0 = partsList.get(index + 1).vertIndices.get(0);
                    roofFace = new ArrayList<>();
                }

                if (part.reflection != null && !part.reflection) {
                    index--;
                    continue;
                }
                //extend <roofFace> with vertex indices from <part>
                debugMsg("        part="+part.vertIndices);
                roofFace.addAll(part.vertIndices);

                debugMsg("        part._index="+part._index);
                debugMsg("        indexPartR="+indexPartR);
                debugMsg("        partsR="+this.n.partsR);

                if (part.vertIndices.get(part.vertIndices.size() - 1).equals(vertIndex0)) {
                    roofIndices.add(roofFace);
                    vertIndex0 = null;
                } else if (!endAtSelf.get(part._index)) {
                    int ii;
                    if (indexPartR >= 0) {ii=indexPartR;} else{ii=n.partsR.size()+indexPartR;}
                    if (!n.partsR.isEmpty() ) {
                        roofFace.addAll(n.partsR.get(ii));
                        debugMsg("        second extension:"+n.partsR.get(ii));
                        indexPartR--;
                    }
                    roofIndices.add(roofFace);
                    debugMsg("        roofIndices="+roofIndices);
                    vertIndex0 = null;
                } else if (!part.vertIndices.get(part.vertIndices.size() - 1)
                         .equals(partsList.get(index - 1).vertIndices.get(0))) {
                    index = trackDown(roofIndices,
                        part.reflection != null && part.reflection ? index + 1 : index,
                        part.vertIndices.get(part.vertIndices.size() - 1));
                    if (part.reflection != null && part.reflection) {
                        part.reflection = null;
                    }
                }
                debugMsg("        roofFace="+roofFace);
                if (destVertIndex != null) {
                    if (partsList.get(index - 1).vertIndices.get(0).equals(destVertIndex)) {
                        debugMsg("DEBUG: Slot.trackDown() exit");
                        debugMsg("    Returns: " + index +"\n");
                        return index;
                    } else if (part.reflection != null && part.reflection &&
                               part.vertIndices.get(0).equals(destVertIndex)) {
                        debugMsg("DEBUG: Slot.trackDown() exit");
                        debugMsg("    Returns: " + index +"\n");
                        return index + 1;
                    }
                }

                index -= (part.reflection != null && part.reflection) ? 1 : 2;
            }
            debugMsg("DEBUG: Slot.trackDown() exit");
            debugMsg("    Returns: " + index +"\n");
            return index;
        }

        int trackUp(List<List<Integer>> roofIndices, Integer startIndex, Integer destVertIndex) {
            debugMsg("\nDEBUG: Slot.trackUp() entry");
            debugMsg("    Params: roofIndices=" + roofIndices);
            debugMsg("            startIndex=" + startIndex + ", destVertIndex=" + destVertIndex);
            //debugMsg("      Self:  " + this);

            List<Part> partsList = this.parts;
            int numParts = partsList.size();
            int index = (startIndex == null ? 1 : startIndex + 2);
            Integer vertIndex0 = null;
            List<Integer> roofFace = null;

            while (index < numParts) {
                Part part = partsList.get(index);
                if (vertIndex0 == null) {
                    vertIndex0 = partsList.get(index - 1).vertIndices.get(0);
                    // start a new roof face
                    roofFace = new ArrayList<>();
                }

                if (part.reflection != null && part.reflection) {
                    index++;
                    continue;
                }

                debugMsg("        part=" +part.vertIndices);
                roofFace.addAll(part.vertIndices);
                debugMsg("        roofFace=" +roofFace );

                if (part.vertIndices.get(part.vertIndices.size() - 1).equals(vertIndex0)) {
                    roofIndices.add(roofFace);
                    vertIndex0 = null;
                } else if (!endAtSelf.get(part._index)) {
                    partsR.add(roofFace);
                    vertIndex0 = null;
                } else if (!part.vertIndices.get(part.vertIndices.size() - 1)
                         .equals(partsList.get(index + 1).vertIndices.get(0))) {
                    index = trackUp(roofIndices, index, part.vertIndices.get(part.vertIndices.size() - 1));
                }

                if (destVertIndex != null &&
                    partsList.get(index + 1).vertIndices.get(0).equals(destVertIndex)) {
                    debugMsg("DEBUG: Slot.trackUp() exit");
                    debugMsg("    Returns: " + index +"\n");
                    return index;
                }

                index += (part.reflection != null && !part.reflection) ? 1 : 2;
            }
            debugMsg("DEBUG: Slot.trackUp() exit");
            debugMsg("    Returns: " + index +"\n");
            return index;
        }

        void processWallFace(List<Integer> indices, ProfiledVert pv1, ProfiledVert pv2) {
            //debugMsg("\nDEBUG: Slot.processWallFace() entry");
            //debugMsg("    Params: indices=" + indices +
            //        ", pv1=" + pv1 + ", pv2=" + pv2);
            // This function is blank in blosm
        }

        static class Part {
            double y;
            List<Integer> vertIndices;
            Boolean reflection;
            int _index;

            @Override
            public String toString(){
                return "("+y +", "+vertIndices.toString()+", " + reflection +", " + _index+")";
            }

            public Part(double y, List<Integer> vertIndices, Boolean reflection, int index) {
                this.y = y;
                this.vertIndices = vertIndices;
                this.reflection = reflection;
                this._index=index;
            }
        }
    }

    static class Polygon {
        public Point3D normal;
        public int n=0;
        private List<Point3D> vertices= new ArrayList<>();
        public  List<Integer> indices = new ArrayList<>();

        Polygon(List<Point2D> outerRing){
            this.normal= new Point3D(0., 0., -1.);
            n=outerRing.size();
            for (int i=0;i<n;i++){
                indices.add(n-i-1);
                //var p=outerRing.get(i);
                var p=outerRing.get(n-1-i);
                vertices.add(new Point3D(p.x,p.y,0));
            }

        }

        int next(int i) {
            return (i + 1) % indices.size();
        }
        @Override
        public String toString(){
            return "Polygon: " + indices.toString();

        }
        public List<Point3D> getEdges(){
            List<Point3D> result = new ArrayList<>();
            for (int i:indices){
                Point3D v1 = vertices.get(next(i));
                Point3D v0 = vertices.get(i);
                result.add(v1.subtract(v0));
            }
            return result;

        }
        protected Point3D getDefaultDirection() {

            // a perpendicular to the longest edge of the polygon
            var edges =this.getEdges();
            Point3D maxEdge=null;
            double maxEdgeLength=-1;
            for(var edge:edges){
                if (edge.length()>maxEdgeLength){
                    maxEdgeLength = edge.length();
                    maxEdge = edge;
                }
            }
            return maxEdge.cross(this.normal).normalize();
        }

        //parallel to the longest edge of the polygon
        protected Point3D getAcrossDirection() {

            //# a perpendicular to the longest edge of the polygon
            var edges =this.getEdges();
            Point3D maxEdge=null;
            double maxEdgeLength=-1;
            for(var edge:edges){
                if (edge.length()>maxEdgeLength){
                    maxEdgeLength = edge.length();
                    maxEdge = edge;
                }
            }
            return maxEdge.normalize();
        }

    }
}

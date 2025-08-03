package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.*;

public class MesherLinearProfile2 extends RoofGenerator {

    //main method to be called
    public Mesh generate(RenderableBuildingElement building)
    {
        var profile_data = ROUND_ROOF;
        var roofProfile = new LinearProfileInner(profile_data);
        roofProfile.init(building);
        roofProfile.make();
        return null;
    }

    static final double ZERO = 1e-6;
    
    // Профили крыш
    public Object[] GABLED_ROOF() {
        Object[] profile = new Object[]{
        new Point2D[]{
            new Point2D(0.0, 0.0),
            new Point2D(0.5, 1.0),
            new Point2D(1.0, 0.0)
        },
        Map.of(
            "numSamples", 10,
            "angleToHeight", 0.5
        )};
        return profile;
    };

    public final Object[] ROUND_ROOF = {
            new Point2D[]{
                    new Point2D(0.0, 0.0),
                    new Point2D(0.01, 0.195),
                    new Point2D(0.038, 0.383),
                    new Point2D(0.084, 0.556),
                    new Point2D(0.146, 0.707),
                    new Point2D(0.222, 0.831),
                    new Point2D(0.309, 0.924),
                    new Point2D(0.402, 0.981),
                    new Point2D(0.5, 1.0),
                    new Point2D(0.598, 0.981),
                    new Point2D(0.691, 0.924),
                    new Point2D(0.778, 0.831),
                    new Point2D(0.854, 0.707),
                    new Point2D(0.916, 0.556),
                    new Point2D(0.962, 0.383),
                    new Point2D(0.99, 0.195),
                    new Point2D(1.0, 0.0)
            },
            Map.of(
                    "numSamples", 1000,
                    "angleToHeight", 0.1)

    };


    // Аналогично для других профилей: ROUND_ROOF,  GAMBREL_ROOF, SALTBOX_ROOF

    static class ProfiledVert {
        double x, y;
        int i;
        double h;
        int index;
        boolean onSlot;
        int vertIndex;
        LinearProfileInner roof;

        public ProfiledVert(LinearProfileInner roof, int i, double roofVerticalPosition, boolean noWalls) {
            System.out.println("\nDEBUG: ProfiledVert constructor entry");
            System.out.println("    Params: RoofProfile: " + roof + ", i=" + i +
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
            this.onSlot = onSlot;
            this.vertIndex = vertIndex;
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
            System.out.println("\nDEBUG: Slot constructor entry ");
            System.out.println("    Params: x=" + x);
            this.x = x;
        }
        @Override
        public String toString() {
            return "Slot: x " + this.x + " parts " + this.parts + " partsR " + this.partsR + " endAtSelf  " + this.endAtSelf;
        }


        void reset() {
            System.out.println("\nDEBUG: Slot.reset() entry");
            System.out.println("    Params: ");
            parts.clear();
            partsR.clear();
            endAtSelf.clear();
            index = 0;
        }

        void prepare() {
            System.out.println("\nDEBUG: Slot.prepare() entry");
            System.out.println("    Self: " + this);
            
            parts.sort(Comparator.comparingDouble(p -> p.y));
        }

        void append(int vertIndex) {
            System.out.println("\nDEBUG: Slot.append() entry");
            System.out.println("    Params:  " + vertIndex+ " None None None");
            System.out.println("      Self:  " + this);
            if (!parts.isEmpty()) {
                Part lastPart = parts.get(parts.size() - 1);
                lastPart.vertIndices.add(vertIndex);
            }
        }

        void append(int vertIndex, double y, Slot originSlot, Boolean reflection) {
            System.out.println("\nDEBUG: Slot.append() entry");
            System.out.println("    Params:  " + vertIndex + " " + y +
                    " " + originSlot + " " + reflection);
            System.out.println("      Self:  " + this);
            List<Integer> vertIndices = new ArrayList<>();
            vertIndices.add(vertIndex);
            Part part = new Part(y, vertIndices, reflection);
            parts.add(part);
            originSlot.endAtSelf.add(originSlot == this);
            index++;
        }

        int trackDown(List<List<Integer>> roofIndices, Integer startIndex, Integer destVertIndex) {
            System.out.println("\nDEBUG: Slot.trackDown() entry");
            System.out.println("    Params: roofIndices=" + roofIndices + 
                    ", startIndex=" + startIndex + ", destVertIndex=" + destVertIndex);
            System.out.println("    Self: " + this);
            List<Part> partsList = this.parts;
            int indexPartR = -1;
            int index = (startIndex == null ? partsList.size() : startIndex) - 2;
            Integer vertIndex0 = null;

            while (index >= 0) {
                Part part = partsList.get(index);
                if (vertIndex0 == null) {
                    vertIndex0 = partsList.get(index + 1).vertIndices.get(0);
                }

                if (part.reflection != null && !part.reflection) {
                    index--;
                    continue;
                }

                List<Integer> roofFace = new ArrayList<>(part.vertIndices);
                if (part.vertIndices.get(part.vertIndices.size() - 1).equals(vertIndex0)) {
                    roofIndices.add(roofFace);
                    vertIndex0 = null;
                } else if (!endAtSelf.get(part._index)) {
                    if (!partsR.isEmpty() && indexPartR >= 0) {
                        roofFace.addAll(partsR.get(indexPartR));
                        indexPartR--;
                    }
                    roofIndices.add(roofFace);
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

                if (destVertIndex != null) {
                    if (partsList.get(index - 1).vertIndices.get(0).equals(destVertIndex)) {
                        return index;
                    } else if (part.reflection != null && part.reflection && 
                               part.vertIndices.get(0).equals(destVertIndex)) {
                        return index + 1;
                    }
                }

                index -= (part.reflection != null && part.reflection) ? 1 : 2;
            }
            return index;
        }

        int trackUp(List<List<Integer>> roofIndices, Integer startIndex, Integer destVertIndex) {
            System.out.println("\nDEBUG: Slot.trackUp() entry");
            System.out.println("    Params: roofIndices=" + roofIndices + 
                    ", startIndex=" + startIndex + ", destVertIndex=" + destVertIndex);
            System.out.println("    Self: " + this);
            
            List<Part> partsList = this.parts;
            int numParts = partsList.size();
            int index = (startIndex == null ? 1 : startIndex + 2);
            Integer vertIndex0 = null;

            while (index < numParts) {
                Part part = partsList.get(index);
                if (vertIndex0 == null) {
                    vertIndex0 = partsList.get(index - 1).vertIndices.get(0);
                }

                if (part.reflection != null && part.reflection) {
                    index++;
                    continue;
                }

                List<Integer> roofFace = new ArrayList<>(part.vertIndices);
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
                    return index;
                }

                index += (part.reflection != null && !part.reflection) ? 1 : 2;
            }
            return index;
        }

        void processWallFace(List<Integer> indices, ProfiledVert pv1, ProfiledVert pv2) {
            //System.out.println("\nDEBUG: Slot.processWallFace() entry");
            //System.out.println("    Params: indices=" + indices +
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
                return ""+y +" "+vertIndices.toString()+" " + reflection +" " + _index;


            }

            public Part(double y, List<Integer> vertIndices, Boolean reflection) {
                this.y = y;
                this.vertIndices = vertIndices;
                this.reflection = reflection;
            }
        }
    }

    static class Roof {
        protected RenderableBuildingElement building;
        List<Point3D> verts = new ArrayList<>();
        Polygon polygon;
        List<Double> projections = new ArrayList<>();
        boolean hasRidge = true;
        double defaultHeight = 3.0;
        double roofHeight;
        double polygonWidth;
        int minProjIndex;
        int maxProjIndex;
        Point3D direction;
        boolean noWalls;
        double roofVerticalPosition;
        List<List<Integer>> roofIndices = new ArrayList<>();
        List<List<Integer>> wallIndices = new ArrayList<>();

        public void init(RenderableBuildingElement building) {

            this.building = building;
            // Polygon contains just indices of the base vertices.
            // since we removed unnecessary nodes, this initialization is very simple
            //except order should be reversed
            polygon = new Polygon(building.getContour());

            //filling vertices
            for (int i=0;i<building.getContour().size();i++){
                var p2d = building.getContour().get(i);
                verts.add( new Point3D(p2d.x, p2d.y, building.minHeight));
            }
            roofVerticalPosition = building.wallHeight;
            roofHeight = building.roofHeight;

        }

        public boolean make() {
            throw new RuntimeException("this should not be called!");
            //return false;
        }

        public void makeBottom() {
            // Формирование нижней части
        }

        protected Point3D getDefaultDirection() {

            //# a perpendicular to the longest edge of the polygon
            var edges =polygon.getEdges();
            Point3D maxEdge=null;
            double maxEdgeLength=-1;
            for(var edge:edges){
                if (edge.length()>maxEdgeLength){
                    maxEdgeLength = edge.length();
                    maxEdge = edge;
                }
            }
            return maxEdge.cross(polygon.normal).normalize();
        }
    }

    static class LinearProfileInner extends Roof {
        Point2D[] profile;
        int numSlots;
        int lastProfileIndex;
        int numSamples;
        Double angleToHeight;
        boolean lEndZero;
        boolean rEndZero;
        int[] profileQ;
        Slot[] slots;
        Slot slot;
        Slot originSlot;

        public String toString(){
            String s="";
            s += "verts " + this.verts;
            s += " polygon.indices " + this.polygon.indices;
            s += " projections " + this.projections;
            s += " profile " + this.profile;
            s += " direction " + this.direction;
            s += " lEndZero " + this.lEndZero;
            s += " rEndZero " + this.rEndZero;
            return s;
        }



        public LinearProfileInner(Object[] profile_data) {
            System.out.println("\nDEBUG: RoofProfile constructor entry");
            System.out.println("    Params: data=" + Arrays.toString(profile_data));
            
            this.profile = (Point2D[]) profile_data[0];
            Map<String, Object> attributes = (Map<String, Object>) profile_data[1];
            this.numSamples = (int) attributes.get("numSamples");
            this.angleToHeight = (Double) attributes.get("angleToHeight");
            
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

        @Override
        public void init(RenderableBuildingElement building) {
            System.out.println("\nDEBUG: RoofProfile.init() entry");

            super.init(building);
            initProfile();
        }

        void initProfile() {
            System.out.println("\nDEBUG: RoofProfile.initProfile() entry");
            for (int i = 0; i < lastProfileIndex; i++) {
                slots[i].reset();
            }
        }

        @Override
        public boolean make() {
            System.out.println("\nDEBUG: RoofProfile.make() entry");
            System.out.println("    Params: ");
            if (projections.isEmpty()) {
                processDirection();
            }
            
            _make();
            //on this stage we should have
            /*
                - slots
                - verts
                - polygon, i.e. contour
                - roofIndices
                - projections
                - roofHeight;
                - minProjIndex;
                - direction;
                - roofVerticalPosition;
            */
            System.out.println("        slots:  ");
            for (var s: this.slots) {
                System.out.println("            " + s.toString());
            }

            System.out.println("        polygon: " + polygon.toString().replace("[","(").replace("]",")"));
            System.out.println("        polygonWidth: " + polygonWidth);
            System.out.println("        verts:  " + verts.toString());
            System.out.println("        roofIndices: "+ roofIndices.toString());
            System.out.println("        projections: "+ projections.toString());
            System.out.println("        roofHeight: "+ roofHeight);
            System.out.println("        minProjIndex: "+ minProjIndex);
            System.out.println("        direction: " + direction);
            System.out.println("        roofVerticalPosition: " + roofVerticalPosition);

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
            onRoofForSlotCompleted(0);
            
            for (int j = 1; j < lastProfileIndex; j++) {
                Slot slotL = slotR;
                slotR = slots[j + 1];
                slotR.trackUp(roofIndices, null, null);
                slotL.trackDown(roofIndices, null, null);
                onRoofForSlotCompleted(j);
            }
            
            super.makeBottom();
            return true;
        }

        void _make() {
            //Can be redefined if necessary.
        }

        ProfiledVert getProfiledVert(int i, double roofVerticalPosition, boolean noWalls) {
            System.out.println("\nDEBUG: RoofProfile.getProfiledVert() entry");
            System.out.println("    Params: i=" + i + ", roofVerticalPosition=" + 
                    roofVerticalPosition + ", noWalls=" + noWalls);
            
            return new ProfiledVert(this, i, roofVerticalPosition, noWalls);
        }

        void createProfileVertices(ProfiledVert pv1, ProfiledVert pv2, ProfiledVert _pv) {
            System.out.println("\nDEBUG: RoofProfile.createProfileVertices() entry");
            System.out.println("    Params: pv1=" + pv1 + ", pv2=" + pv2 + ", _pv=" + _pv);
            
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

            System.out.println("\nDEBUG: common_code() entry");
            System.out.println("    Params:  "+ slot);


            List<Point3D> verts = this.verts;
            Point2D[] p = profile;
            Slot[] slots = this.slots;

            int vertIndex = verts.size();
            int count = Math.abs(start - end);
            int vertIndexForSlots = vertIndex + count - 1;
            
            double factorX = (v2.x - v1.x) / (pv2.x - pv1.x);
            double factorY = (v2.y - v1.y) / (pv2.x - pv1.x);
            double factorSlots = (pv2.y - pv1.y) / (pv2.x - pv1.x);

            int current = start;
            for (int i = 0; i < count; i++) {
                double factor = p[current].x - pv1.x;
                double x = v1.x + factor * factorX;
                double y = v1.y + factor * factorY;
                double z = roofVerticalPosition + roofHeight * p[current].y;
                verts.add(new Point3D(x, y, z));
                _wallIndices.add(vertIndex);
                
                slot.append(vertIndexForSlots);
                originSlot = slot;
                slot = slots[current];
                
                double yCoord = pv1.y + factorSlots * (p[current].x - pv1.x);
                slot.append(vertIndexForSlots, yCoord, originSlot, null);
                onNewSlotVertex(current, vertIndexForSlots, yCoord);
                slot.processWallFace(_wallIndices, pv1, pv2);
                
                vertIndex++;
                vertIndexForSlots--;
                current += step;
            }
            System.out.println("\n     : common_code() exit");
            System.out.println("    Returns: " + slot);
            return slot;
        }

        double getRoofHeight() {
            System.out.println("\nDEBUG: RoofProfile.getRoofHeight() entry");
            System.out.println("    Params: ");
            
            // Заглушка для парсинга тегов
            double h = defaultHeight;
            // Реальная реализация должна парсить теги OSM
            return h;
        }

        void processDirection() {

            // <d> stands for direction

            String ds = this.building.tags.get("roof:direction");
            if (ds == null) {
                ds = this.building.tags.get("roof:slope:direction");
            }
            Double d=Double.NaN;
            if (ds != null && !ds.isEmpty()){
                d = this.building.parseDirection(ds);
            }

            if ( ds==null || Double.isNaN(d)){
                if (this.hasRidge && "across".equals(building.tags.get("roof:orientation"))) {
                    // The roof ridge is across the longest side of the building outline,
                    // i.e. the profile direction is along the longest side
                    var edges = polygon.getEdges();
                    for (var edge:edges){

                    }
                    //d = max(this.polygon.edges()).normalized();
                }
            }

            // the direction vector is used by <profile.RoofProfile>
            if(d==null || Double.isNaN(d)) {
                this.direction = this.getDefaultDirection();
            }else{
                d = Math.toRadians(d);
                this.direction = new Point3D(Math.sin(d), Math.cos(d), 0.);
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

        void onNewSlotVertex(int slotIndex, int vertIndex, double y) {
            // this function is empty in blender-osm.
        }

        void onRoofForSlotCompleted(int slotIndex) {
            System.out.println("\nDEBUG: RoofProfile.onRoofForSlotCompleted() entry");
            System.out.println("    Params: slotIndex=" + slotIndex);
            // Может быть переопределено
        }
    }

    // Вспомогательные классы
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
                var p=outerRing.get(i);
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
    }

}
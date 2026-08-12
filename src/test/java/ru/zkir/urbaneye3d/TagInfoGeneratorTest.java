package ru.zkir.urbaneye3d;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TagInfoGeneratorTest {

    private static final class ParsedTag {
        private final String key;
        private final String value;
        private final String originalKey;

        private ParsedTag(String key, String value, String originalKey) {
            this.key = key;
            this.value = value;
            this.originalKey = originalKey;
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }

        public String originalKey() {
            return originalKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParsedTag parsedTag = (ParsedTag) o;
            return Objects.equals(key, parsedTag.key) && Objects.equals(value, parsedTag.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }

    /** This is our dictionary for tag (key=value) documentation */
    private final Map<String, String> TAG_DESCRIPTIONS = new HashMap<>();
    {
        TAG_DESCRIPTIONS.put("amenity=bench", "A bench, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("bench=yes", "Indicates the presence of a bench. Used for example on highway=bus_stop nodes.");
        TAG_DESCRIPTIONS.put("bench=no", "Used to explicitly indicate the absence of a bench, for example on a bus stop.");
        TAG_DESCRIPTIONS.put("backrest=no", "Used in combination with amenity=bench to signify that the bench does not have a backrest");
        TAG_DESCRIPTIONS.put("backrest=yes", "Used in combination with amenity=bench to signify whether the bench has a backrest ");
        TAG_DESCRIPTIONS.put("amenity=waste_basket", "A waste basket, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("amenity=bicycle_parking", "A bicycle parking, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("bicycle_parking=stands", "Stands (staple/U-rack) type of bicycle parking. This is the default type in UrbanEye3D.");
        TAG_DESCRIPTIONS.put("leisure=picnic_table", "A picnic table, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("barrier", "The feature is interpreted as barrier, in case it does not have the building tag.");
        TAG_DESCRIPTIONS.put("barrier=block", "A concrete rectangular block, often used to prevent vehicle access.");
        TAG_DESCRIPTIONS.put("barrier=bollard", "A sturdy, short, vertical post. Rendered as a red-and-white striped bollard.");
        TAG_DESCRIPTIONS.put("barrier=gate", "A swinging double-leaf gate. Rendered as a 3D model, and also creates a physical gap in the parent linear barrier.");
        TAG_DESCRIPTIONS.put("barrier=lift_gate", "A boom barrier. Rendered as a 3D model, and also creates a physical gap in the parent linear barrier.");
        TAG_DESCRIPTIONS.put("building", "The main tag for identifying a building outline.");
        TAG_DESCRIPTIONS.put("building:part", "Identifies a part of a building, which is rendered as a separate 3D element.");
        TAG_DESCRIPTIONS.put("building:colour", "Specifies the color of the building's walls.");
        TAG_DESCRIPTIONS.put("building:material", "Specifies the material of the building's walls. Used for texturing and shading.");
        TAG_DESCRIPTIONS.put("building:height", "An alternative tag for the total height of the building, including the roof, in meters.");
        TAG_DESCRIPTIONS.put("building:levels", "The number of floors (levels) in the main part of the building. Used to calculate height if not specified explicitly.");
        TAG_DESCRIPTIONS.put("building:min_level", "The number of floors to offset the building from the ground. Used to calculate min_height if not specified explicitly.");
        TAG_DESCRIPTIONS.put("circumference", "Used to estimate height of trees (natural=tree)");
        TAG_DESCRIPTIONS.put("colour", "Specifies the color of the object, especially barrier or man-made.");
        TAG_DESCRIPTIONS.put("height", "The total height of the building, including the roof, in meters.");
        TAG_DESCRIPTIONS.put("highway=bus_stop", "A bus stop, rendered as a 3D model if it has shelter=yes.");
        TAG_DESCRIPTIONS.put("shelter=yes", "Indicates that a bus stop has a shelter, triggering appropriate 3D model rendering.");
        TAG_DESCRIPTIONS.put("shelter=no", "Indicates that a bus stop does NOT have a shelter, triggering appropriate 3D model rendering.");
        TAG_DESCRIPTIONS.put("highway=street_lamp", "A single street lamp, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("lamp_mount=bent_mast", "A street lamp with a curved (bent) mast.");
        TAG_DESCRIPTIONS.put("lamp_mount=straight_mast", "A street lamp with a standard straight mast.");
        TAG_DESCRIPTIONS.put("emergency=fire_hydrant", "A fire hydrant, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("historic=memorial", "A memorial or monument, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("memorial=statue", "A memorial statue (personage/animal), rendered as 3D model.");
        TAG_DESCRIPTIONS.put("memorial=sculpture", "A memorial non figurative sculpture, rendered as 3D model.");
        TAG_DESCRIPTIONS.put("memorial=bust", "A memorial bust, rendered as a 3D model on a pedestal.");
        TAG_DESCRIPTIONS.put("memorial=obelisk", "A memorial obelisk, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("memorial=war_memorial", "A war memorial, often rendered as an obelisk.");
        TAG_DESCRIPTIONS.put("memorial=stone", "A memorial stone or boulder with a plaque, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("memorial=stele", "A stone stele or vertical slab with carvings, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("fire_hydrant:type=pillar", "Traditional fire hydrant shape. Also the default one.");
        TAG_DESCRIPTIONS.put("fire_hydrant:type=underground", "Underground hydrants are NOT rendered!");
        TAG_DESCRIPTIONS.put("public_transport=platform", "A public transport platform. Rendered as a bus stop model if applicable.");
        TAG_DESCRIPTIONS.put("tourism=information", "General tourism information point.");
        TAG_DESCRIPTIONS.put("tourism=artwork", "An artwork, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("artwork_type=statue", "A figurative statue, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("artwork_type=sculpture", "A non-figurative sculpture, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("artwork_type=installation", "Installation is considered to be a non-figurative sculpture, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("information=board", "An information board, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("information=post", "A generic information post.");
        TAG_DESCRIPTIONS.put("information=guidepost", "A signpost with arrows pointing to different locations.");
        TAG_DESCRIPTIONS.put("layer", "Objects with layer<0 are considered to be located underground -- and are not displayed");
        TAG_DESCRIPTIONS.put("material","Material for barrier or man-made object. This can influence the default color. ");
        TAG_DESCRIPTIONS.put("min_height", "The height of the ground floor of the building from the ground, in meters. Used to model buildings on stilts or slopes.");
        TAG_DESCRIPTIONS.put("natural=tree", "A single tree, rendered as a 3D billboard model.");
        TAG_DESCRIPTIONS.put("natural=shrub", "A single shrub or bush, rendered as a 3D billboard model.");
        TAG_DESCRIPTIONS.put("natural=spring", "A natural spring, rendered as a small blue circle on the 2D ground plane.");
        TAG_DESCRIPTIONS.put("natural=wood", "A forested area. Automatically populated with 3D tree objects based on the forest density setting.");
        TAG_DESCRIPTIONS.put("species", "The Latin name of the tree species. Used to infer leaf_type and leaf_cycle if these tags are not specified.");
        TAG_DESCRIPTIONS.put("genus", "The Latin name of the tree genus. Used to infer leaf_type and leaf_cycle if these tags are not specified.");
        TAG_DESCRIPTIONS.put("landuse=forest", "A managed forest area. Automatically populated with 3D tree objects based on the forest density setting.");
        TAG_DESCRIPTIONS.put("leaf_type=broadleaved", "Used to select an appropriate texture/model for trees.");
        TAG_DESCRIPTIONS.put("leaf_type=needleleaved", "Used to select an appropriate texture/model for trees.");
        TAG_DESCRIPTIONS.put("leaf_type=palm", "Used to select an appropriate texture/model for trees.");
        TAG_DESCRIPTIONS.put("roof:colour", "Specifies the color of the roof.");
        TAG_DESCRIPTIONS.put("direction", "Specifies the direction an object is facing (e.g., for benches), in degrees or cardinal points.");
        TAG_DESCRIPTIONS.put("roof:direction", "Specifies the direction or orientation of the roof, typically in degrees. Used for directional roof shapes like 'skillion'.");
        TAG_DESCRIPTIONS.put("roof:height", "The height of the roof section of the building, in meters.");
        TAG_DESCRIPTIONS.put("roof:levels", "The number of floors (levels) within the roof structure. Used to calculate roof:height if not specified explicitly.");
        TAG_DESCRIPTIONS.put("roof:material", "Specifies the material of the roof. Used for texturing and shading.");
        TAG_DESCRIPTIONS.put("roof:orientation", "Specifies the orientation of the roof ridge, typically 'along' or 'across' the longer axis of the building.");
        TAG_DESCRIPTIONS.put("roof:orientation=along", "Roof is oriented along the longest side of the building. This is default value");
        TAG_DESCRIPTIONS.put("roof:orientation=across", "Roof is oriented across the longest side of the building.");

        TAG_DESCRIPTIONS.put("step:height", "Defines the height of each individual step for building:part=steps. 0.16 is used as default ");
        TAG_DESCRIPTIONS.put("type=multipolygon", "Members of a multipolygon relation can be downloaded automatically to prevent broken geometry");
        TAG_DESCRIPTIONS.put("type=building", "Members of building relation can be downloaded automatically, to prevent incomplete buildings");

        TAG_DESCRIPTIONS.put("width", "Width of the feature. Primarily used for barriers");
        TAG_DESCRIPTIONS.put("hyperboloid:top_rate", "Defines the relative width of the top of the structure compared to its base");
        TAG_DESCRIPTIONS.put("hyperboloid:middle_rate", "Defines the relative width of the narrowest part (the \"waist\") of the structure, as a ratio of the base width");

        TAG_DESCRIPTIONS.put("area=yes", "This tag causes a feature that would otherwise be considered linear to be considered polygonal.");
        TAG_DESCRIPTIONS.put("location=underground", "An object is located underground. Can be ignored for 3D rendering");
        TAG_DESCRIPTIONS.put("shape=hyperboloid", "Building or man-made object has hyperboloid shape. Useful for cooling towers and chimneys. Homebrew SM3DB extension");
        TAG_DESCRIPTIONS.put("wall=yes", "Used to override building:part=roof. For building:part=roof + wall=yes walls are still created");
        TAG_DESCRIPTIONS.put("wall=no", "Alternative for building:part=roof. In case of building:part=yes + wall=no walls are not generated and roof floats");

        TAG_DESCRIPTIONS.put("building:part=roof", "Produces a floating roof without walls. Could be used to model canopies, awnings, or visors." );
        TAG_DESCRIPTIONS.put("building:part=steps", "Produces steps-shaped structure or a flight of stairs. Should be used together with roof:shape=skillion" );

        TAG_DESCRIPTIONS.put("roof:shape=apse_gabled", "A gabled roof with a semicircular apse at one end. The apex of the apse is located above the middle of the longest side of the base.");
        TAG_DESCRIPTIONS.put("tourism=artwork", "General tag for a work of art.");
        TAG_DESCRIPTIONS.put("artwork_type=statue", "A three-dimensional work of art representing a person or animal, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("artwork_type=sculpture", "A three-dimensional non-figurative work of art, rendered as a 3D model.");
        TAG_DESCRIPTIONS.put("shape=star", "A star-shaped object, used for some types of memorials.");
        TAG_DESCRIPTIONS.put("roof:shape=cone", "A synonym for pyramidal. Rises to a single point. To generate a smooth cone, the building outline should be a circle with a sufficient number of nodes.");
        TAG_DESCRIPTIONS.put("roof:shape=cross_gabled" , "Two gabled roof sections at right angles. Only supported for quadrangular footprints. `roof:orientation` and `roof:direction` have no effect.");
        TAG_DESCRIPTIONS.put("roof:shape=crosspitched" , "A synonym for cross_gabled. Two gabled roof sections put together at right angles. Only quadrangular footprints are supported.");
        TAG_DESCRIPTIONS.put("roof:shape=dome" , "A hemispherical roof suitable for any base shape. A circular base produces a traditional dome, while a rectangular base produces a modern, elongated dome.");
        TAG_DESCRIPTIONS.put("roof:shape=flat" , "A standard flat roof. A fascia (vertical decorative band) is created if `roof:height` or `roof:levels` are specified.");
        TAG_DESCRIPTIONS.put("roof:shape=gabled" , "Classic pitched roof. Orientation can be controlled with `roof:orientation=along|across`. `roof:direction` is also supported but snaps to the nearest axis.");
        TAG_DESCRIPTIONS.put("roof:shape=gambrel" , "A symmetrical two-sided roof with two slopes on each side. The orientation can be specified with the `roof:orientation` tag.");
        TAG_DESCRIPTIONS.put("roof:shape=half-dome" , "Half of a dome, with the apex located above the middle of the longest side of the base. Useful for orthodox church apses.");
        TAG_DESCRIPTIONS.put("roof:shape=half-hipped" , "A mix of gabled and hipped styles. Orientation can be set with `roof:orientation`. Only supported for quadrangular building outlines.");
        TAG_DESCRIPTIONS.put("roof:shape=hipped" , "All sides slope down to the walls. Generated using a straight skeleton algorithm, which can create complex shapes for non-convex buildings.");
        TAG_DESCRIPTIONS.put("roof:shape=mansard" , "A four-sided gambrel-style hip roof with two slopes on each side. Currently only supported for quadrangular building outlines.");
        TAG_DESCRIPTIONS.put("roof:shape=onion" , "An onion-shaped dome. The building outline defines the tholobate (drum), and the onion dome is generated on top of it, getting wider than the base.");
        TAG_DESCRIPTIONS.put("roof:shape=pyramidal", "A roof that rises to a single point, generated from an arbitrary base outline. `roof:orientation` and `roof:direction` have no effect.");
        TAG_DESCRIPTIONS.put("roof:shape=round" , "A semi-cylindrical or hangar-like roof. Its orientation can be controlled with the `roof:orientation` tag.");
        TAG_DESCRIPTIONS.put("roof:shape=side_hipped" , "A roof hipped from one side and gabled from the other. Orientation is controlled by `roof:direction`. Supported for quadrangular footprints only.");
        TAG_DESCRIPTIONS.put("roof:shape=saltbox", "Generates a roof with an off-center flat top. This implementation follows the F4 map interpretation, which may differ from a classic single-ridge saltbox roof.");
        TAG_DESCRIPTIONS.put("roof:shape=skillion" , "A single-sloped roof surface (a lean-to). The slope direction is controlled by the `roof:direction` tag, which can be set to any angle.");
        TAG_DESCRIPTIONS.put("roof:shape=side_half-hipped", "A roof half-hipped from one side and gabled from the other. Orientation is controlled by `roof:direction`. Supported for quadrangular footprints only.");
        TAG_DESCRIPTIONS.put("roof:shape=many", "Interpreted as hipped for buildings (as better than flat), but not for building parts");

        TAG_DESCRIPTIONS.put("man_made", "Used to identify man-made objects");
        TAG_DESCRIPTIONS.put("man_made=communications_tower", "Can be rendered as 3D object");
        TAG_DESCRIPTIONS.put("man_made=cooling_tower",        "Can be rendered as 3D object");
        TAG_DESCRIPTIONS.put("man_made=manhole",              "A manhole cover, rendered as a tiny black dot on the 2D ground plane.");
        TAG_DESCRIPTIONS.put("man_made=tower",                "Can be rendered as 3D object");
        TAG_DESCRIPTIONS.put("man_made=water_tower",          "Can be rendered as 3D object");

        TAG_DESCRIPTIONS.put("power=line", "A high-voltage power line, rendered with multiple sagging wires.");
        TAG_DESCRIPTIONS.put("power=minor_line", "A low-voltage or service power line, rendered with two sagging wires.");
        TAG_DESCRIPTIONS.put("power=tower", "A large lattice power tower support.");
        TAG_DESCRIPTIONS.put("power=pole", "A standard power pole support.");

        TAG_DESCRIPTIONS.put("place", "place=* are NOT rendered and are EXCLUDED from multipolygon automatic download to save performance");

        TAG_DESCRIPTIONS.put("advertising=column",           "Can be rendered as 3D object");

        TAG_DESCRIPTIONS.put("amenity=recycling", "A recycling point. Rendered as a 3D container model.");
        TAG_DESCRIPTIONS.put("recycling_type=container", "Used with amenity=recycling to specify that the recycling point is a container.");
        TAG_DESCRIPTIONS.put("recycling_type=centre", "A larger recycling center, usually containing multiple containers. In UrbanEye3D, we exclude it from simple container rendering.");
        TAG_DESCRIPTIONS.put("amenity=waste_disposal", "A waste disposal point. Currently rendered using the same model as recycling containers.");
        TAG_DESCRIPTIONS.put("amenity=drinking_water", "A drinking water point, rendered as a 3D model (water column).");

        TAG_DESCRIPTIONS.put("leisure=pitch", "A sports pitch. If sport=soccer, tennis, volleyball, badminton or futsal, characteristic markings are rendered on the ground texture.");
        TAG_DESCRIPTIONS.put("sport=soccer", "Indicates that the pitch is used for soccer. Triggers rendering of soccer markings.");
        TAG_DESCRIPTIONS.put("sport=tennis", "Indicates that the pitch is used for tennis. Triggers rendering of tennis court markings.");
        TAG_DESCRIPTIONS.put("sport=volleyball", "Indicates that the pitch is used for volleyball. Triggers rendering of volleyball court markings.");
        TAG_DESCRIPTIONS.put("sport=badminton", "Indicates that the pitch is used for badminton. Triggers rendering of badminton court markings.");
        TAG_DESCRIPTIONS.put("sport=futsal", "Indicates that the pitch is used for futsal. Triggers rendering of futsal markings.");

        //add values from Materials enum. It is good enough description, so we can use it.
        for (var mat:Materials.values()){
            if (!TAG_DESCRIPTIONS.containsKey("building:material" +"="+ mat.displayName)){
                TAG_DESCRIPTIONS.put("building:material" +"="+ mat.displayName, "Recognized as material for a building facade. Default color " + mat.defaultColour );
            }
            if (!TAG_DESCRIPTIONS.containsKey("roof:material" +"="+ mat.displayName)){
                TAG_DESCRIPTIONS.put("roof:material" +"="+ mat.displayName, "Recognized as material for a roof. Default color " + mat.defaultColour);
            }
        }

        //add values from 2D mapCSS.
        HashSet<ParsedTag> usedTags = new HashSet<>();
        extractTagsFromMapCSS(usedTags, "/mapcss-styles/urbaneye2d.roads.mapcss");
        for (var usedTag: usedTags){
            if (!TAG_DESCRIPTIONS.containsKey(usedTag.originalKey())){
                TAG_DESCRIPTIONS.put(usedTag.originalKey(), "Used for 2D ground plane rendering" );
            }
        }
    }

    /** This list contains keys for which reporting of each particular value is not required.*/
    final Set<String> KEYS_WITH_UNDOCUMENTED_VALUES = Set.of("roof:direction", "barrier");

    @Test
    void testGenerateAndValidateTagInfo() throws IOException {

        Set<String> errorMessages = new HashSet<>();

        // 1. Find all unique tags used in the source code
        Set<ParsedTag> usedTags = findTagsInSourceCode();

        // Add tags from TextureManager
        TextureManager.getInstance().getAllTags().stream()
                .map(entry -> new ParsedTag(entry.getKey(), entry.getValue(), entry.getKey() + "=" + entry.getValue()))
                .forEach(usedTags::add);

        extractTagsFromMapCSS(usedTags, "/assets.mapcss");

        // this file is not parsed properly, because it contains too many comments, which are still fetch by RE
        //extractTagsFromMapCSS(usedTags, "/mapcss-styles/urbaneye2d.general.mapcss");
        extractTagsFromMapCSS(usedTags, "/mapcss-styles/urbaneye2d.roads.mapcss");

        //Also add tags from Materials enum
        for (var mat:Materials.values()){
            usedTags.add( new ParsedTag("building:material", mat.displayName , "building:material" +"="+ mat.displayName));
            usedTags.add( new ParsedTag("roof:material", mat.displayName ,"roof:material" + "=" + mat.displayName));
        }

        // 2. Find all described tags.
        Set<ParsedTag> describedTags = TAG_DESCRIPTIONS.keySet().stream()
                .map(this::parseDescriptionKey)
                .collect(Collectors.toSet());

        // For faster lookups, create a set of all key parts that have descriptions
        Set<String> allDescribedKeyParts = describedTags.stream()
                .map(ParsedTag::key)
                .collect(Collectors.toSet());

        // 3. Validation
        // Assert that all tags used in the code have a description
        for (ParsedTag usedTag : usedTags) {
            boolean isDocumented;
            if (usedTag.value() != null) {
                // For key-value pairs, check for a specific match first, then a generic key match
                isDocumented = TAG_DESCRIPTIONS.containsKey(usedTag.originalKey()) ||
                               TAG_DESCRIPTIONS.containsKey(usedTag.key());
            } else {
                // For generic keys, check if the key itself is described OR if any specific value for that key is described
                isDocumented = TAG_DESCRIPTIONS.containsKey(usedTag.key()) ||
                               allDescribedKeyParts.contains(usedTag.key());
            }
            if (!isDocumented){
                errorMessages.add( "Tag '" + usedTag.originalKey() + "' is used in code but lacks a specific or generic description.");
            }

        }

        // Assert that all described tags are actually used in the code (no obsolete tags)
        Set<String> usedKeys = usedTags.stream().map(ParsedTag::key).collect(Collectors.toSet());
        for (ParsedTag describedTag : describedTags) {
            if(!usedKeys.contains(describedTag.key())) {
                errorMessages.add("Tag '" + describedTag.originalKey() + "' is described in TAG_DESCRIPTIONS but its key is not used in the code.");
            }
        }
        assertEquals(0, errorMessages.size(), String.join("\n", errorMessages));

        // 4. Generate the JSON content
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("data_format", 1);
        root.put("data_updated", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")));
        
        ObjectNode project = mapper.createObjectNode();
        project.put("name", "Urban Eye 3D");
        project.put("description", "JOSM plugin for 3D visualization of buildings");
        project.put("project_url", "https://github.com/Zkir/UrbanEye3D");
        project.put("doc_url", "https://github.com/Zkir/UrbanEye3D/blob/master/README.md");
        project.put("icon_url", "https://raw.githubusercontent.com/Zkir/UrbanEye3D/refs/heads/master/docs/images/urbaneye3d.svg");
        project.put("contact_name", "Zkir");
        project.put("contact_email", "zkir@zkir.ru");
        root.set("project", project);

        ArrayNode tagsNode = root.putArray("tags");
        describedTags.stream()
                .sorted(Comparator.comparing(ParsedTag::originalKey))
                .forEach(parsedTag -> {
                    ObjectNode tagNode = mapper.createObjectNode();
                    tagNode.put("key", parsedTag.key());
                    if (parsedTag.value() != null) {
                        tagNode.put("value", parsedTag.value());
                    }
                    tagNode.put("description", TAG_DESCRIPTIONS.get(parsedTag.originalKey()));
                    tagsNode.add(tagNode);
                });
        
        String newJsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        // 5. Validate generated JSON against the schema
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V6);
        try (InputStream schemaStream = Files.newInputStream(Paths.get("docs/taginfo-project-schema.json"))) {
            JsonSchema schema = factory.getSchema(schemaStream);
            JsonNode jsonNode = mapper.readTree(newJsonContent);
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                String errorsMessages1 = errors.stream()
                        .map(ValidationMessage::toString)
                        .collect(Collectors.joining("\n"));
                fail("JSON schema validation failed:\n" + errorsMessages1);
            }
        }

        // 6. Conditionally write the file
        Path outputPath = Paths.get("docs/taginfo.json");
        if (Files.exists(outputPath)) {
            String oldJsonContent = new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8);
            JsonNode oldNode = mapper.readTree(oldJsonContent);
            JsonNode newNode = mapper.readTree(newJsonContent);

            ((ObjectNode) oldNode).remove("data_updated");
            ((ObjectNode) newNode).remove("data_updated");

            if (oldNode.equals(newNode)) {
                //System.out.println("✅ taginfo.json is up-to-date. No changes needed.");
                return;
            }
        }
        
        Files.write(outputPath, newJsonContent.getBytes(StandardCharsets.UTF_8));
        //System.out.println("✅ Generated and updated taginfo.json with descriptions.");
        assertTrue(Files.exists(outputPath), "taginfo.json file was not created.");
    }

    private void extractTagsFromMapCSS(Set<ParsedTag> usedTags, String filename) {
        // Add tags from assets.mapcss
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // Simple regex to extract tags from [key=value] or [key]
                Pattern tagPattern = Pattern.compile("\\[\\s*([^\\]=!<>~\\s]+)\\s*(?:(!=|~=|=)\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\]]+))\\s*)?\\]");
                Matcher tagMatcher = tagPattern.matcher(content);
                while (tagMatcher.find()) {
                    String key = tagMatcher.group(1).trim();
                    String value = null;
                    if (tagMatcher.group(3) != null) value = tagMatcher.group(3);
                    else if (tagMatcher.group(4) != null) value = tagMatcher.group(4);
                    else if (tagMatcher.group(5) != null) value = tagMatcher.group(5).trim();

                    usedTags.add(new ParsedTag(key, value, value == null ? key : key + "=" + value));
                }
            } else {
                throw new IllegalStateException("/assets.mapcss not found");
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not parse assets.mapcss for TagInfo", e);
        }
    }

    private ParsedTag parseDescriptionKey(String key) {
        String[] parts = key.split("=", 2);
        if (parts.length == 2) {
            return new ParsedTag(parts[0], parts[1], key);
        }
        return new ParsedTag(key, null, key);
    }

    private Set<ParsedTag> findTagsInSourceCode() throws IOException {
        Set<ParsedTag> tags = new HashSet<>();
        Pattern pattern1 = Pattern.compile("(?<!properties\\.|POWER_ATTACHMENTS\\.)(?:getTagStr|getTagD|get|hasKey|hasTag)\\s*\\(\\s*\"([a-zA-Z0-9:_.-]+)\"\\s*[,\\)]");
        Pattern pattern2 = Pattern.compile("inheritableKeys\\s*=\\s*Arrays\\.asList\\(([^)]+)\\)");
        Pattern pattern3 = Pattern.compile("hasTag\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");

        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java/ru/zkir/urbaneye3d"))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                            Matcher matcher1 = pattern1.matcher(content);
                            while (matcher1.find()) {
                                tags.add(new ParsedTag(matcher1.group(1), null, matcher1.group(1)));
                            }

                            Matcher matcher2 = pattern2.matcher(content);
                            if (matcher2.find()) {
                                String block = matcher2.group(1);
                                Pattern stringLiteralPattern = Pattern.compile("\"([a-zA-Z0-9:_.-]+)\"");
                                Matcher stringMatcher = stringLiteralPattern.matcher(block);
                                while (stringMatcher.find()) {
                                    tags.add(new ParsedTag(stringMatcher.group(1), null, stringMatcher.group(1)));
                                }
                            }
                            
                            Matcher matcher3 = pattern3.matcher(content);
                            while (matcher3.find()) {
                                String key = matcher3.group(1);
                                String value = matcher3.group(2);
                                tags.add(new ParsedTag(key, value, key + "=" + value));
                            }

                        } catch (IOException e) {
                            fail("Error processing file " + path, e);
                        }
                    });
        }
        return tags;
    }

    @Test
    void testFeaturesMarkdownIsSynced() throws IOException {
        // 1. Prepare validation data from TAG_DESCRIPTIONS
        Set<String> describedFullKeys = TAG_DESCRIPTIONS.keySet();

        // Create a set of all unique key parts (e.g., "roof:orientation" from "roof:orientation=along")
        Set<String> describedKeyParts = TAG_DESCRIPTIONS.keySet().stream()
                .map(key -> key.split("=")[0])
                .collect(Collectors.toSet());

        // Create a set of all unique values from key-value pairs
        Set<String> describedValues = TAG_DESCRIPTIONS.keySet().stream()
                .map(this::parseDescriptionKey)
                .filter(p -> p.value() != null)
                .map(ParsedTag::value)
                .collect(Collectors.toSet());

        // 2. Find all tags in the markdown file
        String markdownContent = new String(Files.readAllBytes(Paths.get("docs/features.md")), StandardCharsets.UTF_8);
        Set<String> markdownTags = new TreeSet<>();
        Pattern markdownPattern = Pattern.compile("`([a-zA-Z0-9:_.-]+(?:=[a-zA-Z0-9:_.-]+)?)`");
        Matcher matcher = markdownPattern.matcher(markdownContent);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (tag.endsWith("=*")) {
                tag = tag.substring(0, tag.length() - 2);
            }
            markdownTags.add(tag);
        }

        // 3. Validate each tag found in markdown and collect errors
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (String mdTag : markdownTags) {
            boolean isPair = mdTag.contains("=");
            if (isPair) {
                String keyPart = mdTag.split("=")[0];
                if (KEYS_WITH_UNDOCUMENTED_VALUES.contains(keyPart)) {
                    // Relaxed rule: only check if the base key is documented
                    if (!describedKeyParts.contains(keyPart)) {
                        errors.add("Key '" + keyPart + "' from pair '" + mdTag + "' in features.md is not documented in TAG_DESCRIPTIONS.");
                    }
                } else {
                    // Strict rule: must exist exactly in TAG_DESCRIPTIONS
                    if (!describedFullKeys.contains(mdTag)) {
                        errors.add("Tag pair '" + mdTag + "' from features.md is not defined as a key in TAG_DESCRIPTIONS.");
                    }
                }
            } else {
                // Rule for single tags: must be a key part or a value in TAG_DESCRIPTIONS
                boolean isKeyPart = describedKeyParts.contains(mdTag);
                boolean isValue = describedValues.contains(mdTag);
                if (!isKeyPart && !isValue) {
                    errors.add("Tag/value '" + mdTag + "' from features.md is not found as a key or value in TAG_DESCRIPTIONS.");
                }
            }
        }

        if (!errors.isEmpty()) {
            fail("Found synchronization issues between features.md and TAG_DESCRIPTIONS:\n" + String.join("\n", errors));
        }
    }
}


### План реализации поддержки `natural=tree` (Версия 9)

**Философия:** Минимум классов. Избегаем излишней абстракции и гранулярности, пока это не станет абсолютно необходимо. "Clear Code -- Horrible Performance".

**Исходная точка:** Рефакторинг `Mesh.java` и `Renderer3D.java` завершен.

**Цель:** Реализовать поддержку `natural=tree`, используя существующую классовую структуру с минимальными доработками.

---

#### **Шаг 0: Рефакторинг `RenderableBuildingElement`**

**Цель:** Превратить существующий класс в универсальный контейнер для любого отображаемого объекта.

1.  **Переименовать:**
    *   Файл `RenderableBuildingElement.java` -> `RenderableElement.java`.
    *   Класс `RenderableBuildingElement` -> `RenderableElement`.
2.  **Обновить использования:** Заменить все упоминания `RenderableBuildingElement` на `RenderableElement` во всем проекте (в `Scene.java`, тестах и т.д.).
3.  **Добавить поле:** В `RenderableElement.java` добавить поле для имени текстуры:
    ```java
    public final String textureName;
    ```
4.  **Добавить конструктор для текстурированных объектов:** Создать новый, простой конструктор в `RenderableElement` для объектов, у которых есть только меш и текстура (например, деревья).
    ```java
    // Конструктор для текстурированных объектов, таких как деревья
    public RenderableElement(OsmPrimitive primitive, Mesh mesh, String textureName) {
        this.primitiveId = primitive.getPrimitiveId();
        this.mesh = mesh;
        this.textureName = textureName;
        this.isSelected = primitive.isSelected();

        // Все остальные "строительные" поля инициализируются значениями по умолчанию или null
        this.origin = primitive.getBBox().getCenter();
        this.roofHeight = 0;
        this.minHeight = 0;
        this.wallHeight = 0;
        this.height = 0;
        this.color = Color.WHITE;
        this.roofColor = Color.WHITE;
        this.bottomColor = Color.WHITE;
        this.roofShape = RoofShapes.FLAT;
        this.roofDirection = Double.NaN;
        this.roofOrientation = "";
        this.noWalls = true;
        this.stepHeight = 0;
        this.hyperboloidTopRate = null;
        this.hyperboloidMiddleRate = null;
        // Поле contour остается null, так как оно не нужно для простых объектов
        this.contour = null;
    }
    ```
    *Важно:* Существующий "большой" конструктор для зданий остается без изменений, но будет принадлежать классу `RenderableElement`.

---

#### **Шаг 1: Создание `TextureManager.java`**

**Цель:** Создать централизованный сервис для загрузки, кеширования и управления жизненным циклом всех текстур (статических, как деревья, и динамических, как тайлы земли).

**Расположение файла:** `src/main/java/ru/zkir/urbaneye3d/TextureManager.java`

**Ключевые методы:**
*   `Texture get(GL2 gl, String name)`: Получает текстуру по имени. Если текстура уже загружена, возвращает ее из кеша. Если нет — загружает ее из ресурсов (например, `/resources/images/trees/{name}.png`), кеширует и возвращает.
*   `void dispose(GL2 gl, String name)`: Удаляет указанную текстуру из памяти GPU и из кеша.
*   `void disposeAll(GL2 gl)`: Удаляет все управляемые текстуры.

**Реализация:**
1.  **Поля класса:**
    ```java
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();
    private final Map<String, String> staticTexturePaths = new HashMap<>(); // "oak" -> "/images/trees/oak.png"
    ```
2.  **Инициализация:** В конструкторе или методе `init()` заполнить `staticTexturePaths` путями к статическим текстурам (деревьям).
3.  **Метод `get()`:** Реализовать логику "загрузка по требованию с кешированием".
4.  **Интеграция:** Создать синглтон-экземпляр `TextureManager` в `UrbanEye3dPlugin` и передавать его в `Renderer3D` и `GroundTile`.

---

#### **Шаг 2: Создание `MesherTree.java`**

**Цель:** Создать утилитный класс для генерации геометрии (меша) дерева.

**Расположение файла:** `src/main/java/ru/zkir/urbaneye3d/generators/MesherTree.java` (в новом пакете `generators`).

**Логика:** Метод `generate` будет принимать `Point3D center`, размеры и создавать меш с двумя скрещенными плоскостями и UV-координатами.

```java
package ru.zkir.urbaneye3d.generators;

import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

public class MesherTree {
    public static Mesh generate(Point3D center, double width, double height) {
        Mesh mesh = new Mesh();
        
        // 1. UV-координаты для всей текстуры
        int uv0 = mesh.addUV(0, 0); int uv1 = mesh.addUV(1, 0);
        int uv2 = mesh.addUV(1, 1); int uv3 = mesh.addUV(0, 1);
        int[] texIndices = new int[]{uv0, uv1, uv2, uv3};

        // 2. Вершины для двух скрещенных прямоугольников
        double halfWidth = width / 2.0;
        int v0 = mesh.addVertex(new Point3D(center.x - halfWidth, center.y, center.z));
        int v1 = mesh.addVertex(new Point3D(center.x + halfWidth, center.y, center.z));
        int v2 = mesh.addVertex(new Point3D(center.x + halfWidth, center.y, center.z + height));
        int v3 = mesh.addVertex(new Point3D(center.x - halfWidth, center.y, center.z + height));
        int v4 = mesh.addVertex(new Point3D(center.x, center.y - halfWidth, center.z));
        int v5 = mesh.addVertex(new Point3D(center.x, center.y + halfWidth, center.z));
        int v6 = mesh.addVertex(new Point3D(center.x, center.y + halfWidth, center.z + height));
        int v7 = mesh.addVertex(new Point3D(center.x, center.y - halfWidth, center.z + height));
        
        // 3. Две грани (четырехугольника)
        mesh.addFace(new int[]{v0, v1, v2, v3}, texIndices);
        mesh.addFace(new int[]{v4, v5, v6, v7}, texIndices);

        return mesh;
    }
}
```

---

#### **Шаг 3: Интеграция в `Scene.java`**

**Цель:** Находить деревья и добавлять их в общий список `renderableElements`.

1.  В `Scene.updateData()` добавить новый блок для обработки деревьев.
2.  **Логика блока:**
    *   Пройтись по всем узлам `dataSet`.
    *   Если узел имеет тег `natural=tree`:
        1.  Определить имя текстуры (например, `"oak"` или `"default_tree"`).
        2.  Определить размеры дерева (ширину, высоту).
        3.  Вызвать `MesherTree.generate(...)`, чтобы получить `Mesh`.
        4.  Создать объект через новый конструктор: `new RenderableElement(node, treeMesh, textureName)`.
        5.  Добавить этот объект в `scene.renderableElements`.

---

#### **Шаг 4: Адаптация `Renderer3D.java`**

**Цель:** Отрисовывать все элементы из единого списка, правильно обрабатывая цветные и текстурированные.

*   Основной цикл отрисовки остается прежним: `for (RenderableElement element : scene.renderableElements)`.
*   **Внутри цикла:**
    *   Получить меш: `Mesh mesh = element.getMesh();`
    *   **Добавить проверку:**
        ```java
        if (element.textureName != null) {
            // Это текстурированный объект (дерево)
            Texture texture = textureManager.get(gl, element.textureName);
            // Возможно, понадобится отдельный метод drawTexturedMesh(gl, mesh, texture)
            // или доработка существующего drawMesh
        } else {
            // Это цветной объект (здание)
            // Логика отрисовки цветного меша, как сейчас
        }
        ```
*   Это полностью соответствует вашей цели — минимальные изменения в логике рендерера при сохранении одного списка объектов.
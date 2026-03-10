import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
import warnings
warnings.filterwarnings('ignore')

# ------------------------------
# 1. Настройки
# ------------------------------
file_path = 'data/trees_cleaned.csv'  # укажите путь к вашему CSV-файлу
chunk_size = 1_000_000       # читаем по 1 млн строк за раз (можно менять)
sample_size = 20_000         # размер выборки для графиков

# ------------------------------
# 2. Загрузка данных чанками
# ------------------------------
print("Загрузка данных...")
chunks = pd.read_csv(file_path, chunksize=chunk_size)

# Собираем общую информацию о типах и первых строках (для дальнейшего анализа)
first_chunk = next(chunks)  # берём первый кусок
all_chunks = [first_chunk]

# Читаем остальные куски и добавляем в список (если нужно посчитать общую статистику)
for chunk in chunks:
    all_chunks.append(chunk)

# Объединяем все куски в один DataFrame (только если помещается в память!)
# Если 30 млн строк не влезают, лучше агрегировать статистику по кускам.
# Для простоты примера объединим, но будьте осторожны!
df = pd.concat(all_chunks, ignore_index=True)
print(f"Всего загружено строк: {len(df)}")

# ------------------------------
# 3. Предварительный просмотр
# ------------------------------
print("\nПервые 5 строк:")
print(df.head())
print("\nИнформация о данных:")
print(df.info())

# ------------------------------
# 4. Выбор только числовых столбцов для корреляции
# ------------------------------
numeric_cols = df.select_dtypes(include=[np.number]).columns.tolist()
print(f"\nЧисловые столбцы: {numeric_cols}")

# ------------------------------
# 4.5. Расчёт основных статистик
# ------------------------------
print("\nОсновные статистики для числовых столбцов:")
print(df[numeric_cols].describe())

# ------------------------------
# 5. Расчёт корреляционной матрицы (Пирсон)
# ------------------------------
print("\nРасчёт корреляции Пирсона...")
corr_matrix = df[numeric_cols].corr(method='pearson')
# Можно также посчитать ранговые корреляции (Спирмена, Кендалла)
#corr_matrix = df[numeric_cols].corr(method='spearman')
#corr_matrix = df[numeric_cols].corr(method='kendall')
print("Корреляционная матрица:")
print(corr_matrix)



# ------------------------------
# 6. Визуализация корреляционной матрицы (тепловая карта)
# ------------------------------
plt.figure(figsize=(12, 10))
sns.heatmap(corr_matrix, annot=True, fmt='.2f', cmap='coolwarm', 
            square=True, linewidths=0.5)
plt.title('Матрица корреляции Пирсона')
plt.tight_layout()
plt.show()

# ------------------------------
# 7. Графики рассеяния для небольшой выборки
# ------------------------------
# Берём случайную подвыборку, чтобы не строить миллионы точек
sample_df = df[numeric_cols].sample(n=min(sample_size, len(df)), random_state=42)

# Попарные графики (scatter matrix) – может занять время, если много столбцов
# Ограничим количество столбцов до 5, чтобы не перегружать визуализацию
if len(numeric_cols) > 5:
    print("\nСтолбцов больше 5, показываем графики только для первых 5.")
    plot_cols = numeric_cols[:5]
else:
    plot_cols = numeric_cols

sns.pairplot(sample_df[plot_cols], diag_kind='kde', plot_kws={'alpha':0.5})
plt.suptitle('Попарные графики рассеяния (выборка)', y=1.02)
plt.show()

# ------------------------------
# 8. Дополнительно: проверка значимости корреляции (пример для двух столбцов)
# ------------------------------
if len(numeric_cols) >= 2:
    col1, col2 = numeric_cols[0], numeric_cols[1]
    # Удаляем пропуски для чистоты
    clean_data = df[[col1, col2]].dropna()
    r, p_value = stats.pearsonr(clean_data[col1], clean_data[col2])
    print(f"\nКорреляция Пирсона между {col1} и {col2}: r = {r:.4f}, p-value = {p_value:.4f}")
    
    # Если нужно больше статистики
    # r_s, p_s = stats.spearmanr(clean_data[col1], clean_data[col2])
    # print(f"Спирмен: r = {r_s:.4f}, p = {p_s:.4f}")

    # ------------------------------
    # 9. Построение модели множественной линейной регрессии
    # ------------------------------
    print("\nПостроение модели для предсказания 'height'...")

    # Выбираем нужные столбцы и удаляем строки с пропусками
    regression_df = df[['height', 'age', 'circumference']].dropna()

    # Определяем зависимую (y) и независимые (X) переменные
    y = regression_df['height']
    X_vars = regression_df[['age', 'circumference']]

    # Добавляем константу для расчёта коэффициента b0 (intercept)
    X = np.c_[np.ones(X_vars.shape[0]), X_vars]

    # Вычисляем коэффициенты методом наименьших квадратов
    try:
        coeffs, _, _, _ = np.linalg.lstsq(X, y, rcond=None)
        b0, b1, b2 = coeffs[0], coeffs[1], coeffs[2]

        print("\n-------------------------------------------")
        print("Уравнение для оценки 'height':")
        print(f"height = {b0:.2f} + {b1:.2f} * age + {b2:.2f} * circumference")
        print("-------------------------------------------")

    except np.linalg.LinAlgError as e:
        print(f"Ошибка при вычислении регрессии: {e}")

    # ------------------------------
    # 10. Дополнительные простые модели регрессии
    # ------------------------------
    print("\nПостроение простых регрессионных моделей...")

    # --- Модель 1: height ~ circumference ---
    print("\nМодель 1: height в зависимости от circumference")
    simple_df_1 = df[['height', 'circumference']].dropna()
    y1 = simple_df_1['height']
    X1_vars = simple_df_1['circumference']
    X1 = np.c_[np.ones(X1_vars.shape[0]), X1_vars]

    try:
        coeffs1, _, _, _ = np.linalg.lstsq(X1, y1, rcond=None)
        print(f"Уравнение: height = {coeffs1[0]:.2f} + {coeffs1[1]:.2f} * circumference")
    except np.linalg.LinAlgError as e:
        print(f"Ошибка при вычислении регрессии: {e}")

    # --- Модель 2: height ~ age ---
    print("\nМодель 2: height в зависимости от age")
    simple_df_2 = df[['height', 'age']].dropna()
    y2 = simple_df_2['height']
    X2_vars = simple_df_2['age']
    X2 = np.c_[np.ones(X2_vars.shape[0]), X2_vars]

    try:
        coeffs2, _, _, _ = np.linalg.lstsq(X2, y2, rcond=None)
        print(f"Уравнение: height = {coeffs2[0]:.2f} + {coeffs2[1]:.2f} * age")
    except np.linalg.LinAlgError as e:
        print(f"Ошибка при вычислении регрессии: {e}")
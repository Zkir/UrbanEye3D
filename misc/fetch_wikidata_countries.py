#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Скрипт для получения списка стран/территорий с ISO-кодами и Q-идентификаторами флагов из Викиданных.
Результат сохраняется в CSV-файл.
"""

import csv
from SPARQLWrapper import SPARQLWrapper, JSON

# SPARQL-запрос (возвращает QID страны, название, alpha-2, alpha-3, QID флага и его метку)
QUERY = """
SELECT DISTINCT ?country ?countryLabel ?isoAlpha2 ?isoAlpha3 ?flag ?flagLabel WHERE {
  ?country wdt:P297 ?isoAlpha2.          # есть alpha-2 код ISO 3166-1
  OPTIONAL { ?country wdt:P163 ?flag. }       # флаг – Q-код элемента
  SERVICE wikibase:label { 
    bd:serviceParam wikibase:language "en". 
  }
}
ORDER BY ?isoAlpha2
"""

def main(output_file="data/25_flags_output/wd_national_flags.csv"):
    # 1. Правильный User-Agent
    user_agent = "UrbanEyeDataPipeline/1.0 (https://github.com/Zkir/UrbanEye3D; zkir@zkir.ru)"
    
    # 2. Создаём клиент с User-Agent
    sparql = SPARQLWrapper("https://query.wikidata.org/sparql", agent=user_agent)
    sparql.setQuery(QUERY)
    sparql.setReturnFormat(JSON)
    sparql.setTimeout(1200)          # 4. Увеличиваем таймаут

    print("Выполняется запрос к Викиданным...")
    try:
        results = sparql.query().convert()
    except Exception as e:
        print(f"Ошибка при выполнении запроса: {e}")
        return

    # Извлекаем заголовки переменных
    variables = results['head']['vars']
    bindings = results['results']['bindings']

    if not bindings:
        print("Запрос не вернул данных.")
        return

    # Преобразуем данные в список словарей
    rows = []
    for binding in bindings:
        row = {}
        for var in variables:
            row[var] = binding[var]['value'] if var in binding else ''
            row[var] = row[var].replace("http://www.wikidata.org/entity/","")
        rows.append(row)

    # Записываем в CSV
    with open(output_file, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=variables)
        writer.writeheader()
        writer.writerows(rows)

    print(f"✅ Сохранено {len(rows)} записей в файл '{output_file}'.")

if __name__ == "__main__":
    # Можно указать имя выходного файла, передав аргумент, например:
    # python fetch_wikidata_countries.py my_data.csv
    import sys
    if len(sys.argv) > 1:
        main(sys.argv[1])
    else:
        main()
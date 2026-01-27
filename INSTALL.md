# Инструкция по установке и запуску

## Шаг 1: Установка Ollama

### Windows
1. Скачайте Ollama с https://ollama.ai/download
2. Запустите установщик
3. После установки откройте терминал и выполните:
```bash
ollama pull llama3.2
```

### Проверка установки Ollama
```bash
ollama list
```
Должна отобразиться модель llama3.2

### Запуск Ollama
Ollama обычно запускается автоматически. Если нет:
```bash
ollama serve
```

## Шаг 2: Установка Python

### Windows
1. Скачайте Python с https://www.python.org/downloads/
2. Запустите установщик
3. ✅ **ОБЯЗАТЕЛЬНО** поставьте галочку "Add Python to PATH"
4. Нажмите "Install Now"

Или через Microsoft Store:
- Найдите "Python 3.12" и установите

### Проверка установки
Откройте новое окно PowerShell/CMD и выполните:
```bash
python --version
```
Должна вывестись версия Python (например: Python 3.12.1)

## Шаг 3: Установка зависимостей

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### Если возникают проблемы с pyaudio на Windows:

```bash
python -m pip install pipwin
pipwin install pyaudio
```

Или скачайте wheel файл:
1. Перейдите на https://www.lfd.uci.edu/~gohlke/pythonlibs/#pyaudio
2. Скачайте файл для вашей версии Python (например: PyAudio‑0.2.14‑cp312‑cp312‑win_amd64.whl)
3. Установите: `python -m pip install PyAudio-0.2.14-cp312-cp312-win_amd64.whl`

## Шаг 4: Настройка (опционально)

Файл `.env` не обязателен, но вы можете создать его для настройки:

```bash
copy .env.example .env
```

Откройте `.env` и измените параметры при необходимости:
```
OLLAMA_BASE_URL=http://localhost:11434/api
OLLAMA_MODEL=llama3.2
```

## Шаг 5: Запуск

### Тестирование без микрофона:
```bash
python test_agent.py
```

### Запуск голосового агента:
```bash
python voice_agent.py
```

## Устранение проблем

### "python" не распознается
- Переустановите Python с галочкой "Add to PATH"
- Или используйте `py` вместо `python`: `py -m pip install -r requirements.txt`

### Ошибка микрофона
- Убедитесь, что микрофон подключен
- Дайте разрешение на использование микрофона
- Проверьте настройки конфиденциальности Windows

### PyAudio не устанавливается
- Используйте pipwin (см. выше)
- Или скачайте wheel файл вручную

### Ollama недоступна
- Проверьте, что Ollama запущена: `ollama serve`
- Убедитесь, что модель загружена: `ollama pull llama3.2`
- Проверьте URL в `.env` файле (по умолчанию: http://localhost:11434/api)

### Модель не найдена
```bash
# Загрузите нужную модель
ollama pull llama3.2

# Или используйте другую модель
export OLLAMA_MODEL=llama2
python voice_agent.py
```

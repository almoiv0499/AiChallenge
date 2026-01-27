import requests
import os
import sys
from dotenv import load_dotenv

# Настройка кодировки для Windows
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except:
        pass

# Загружаем переменные окружения
load_dotenv()

def test_text_to_llm(text, ollama_url=None, model=None):
    """Тестовая функция для отправки текста в Ollama без голосового ввода"""
    ollama_url = ollama_url or os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434/api')
    model = model or os.getenv('OLLAMA_MODEL', 'llama3.2')

    try:
        print(f"📝 Запрос: {text}")
        print(f"🤖 Отправляю запрос в Ollama (модель: {model})...")

        # Формируем запрос для Ollama Chat API
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "user",
                    "content": text
                }
            ],
            "stream": False
        }

        # Отправляем запрос
        response = requests.post(
            f"{ollama_url}/chat",
            json=payload,
            timeout=60
        )

        if response.status_code != 200:
            print(f"❌ Ошибка Ollama API: {response.status_code}")
            print(f"   Ответ: {response.text}")
            return

        # Извлекаем ответ
        result = response.json()
        response_text = result.get("message", {}).get("content", "Нет ответа")
        print(f"\n✅ Ответ LLM:\n{response_text}\n")
        print("=" * 60)

    except requests.exceptions.ConnectionError:
        print(f"❌ Не могу подключиться к Ollama по адресу {ollama_url}")
        print("   Убедитесь, что Ollama запущена: ollama serve")
    except Exception as e:
        print(f"❌ Ошибка: {e}")


def main():
    """Тестирование агента с предопределенными запросами"""
    print("=" * 60)
    print("🧪 ТЕСТИРОВАНИЕ ГОЛОСОВОГО АГЕНТА (только Ollama, без речи)")
    print("=" * 60)
    print(f"\n📋 Конфигурация:")
    print(f"  Ollama URL: {os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434/api')}")
    print(f"  Модель: {os.getenv('OLLAMA_MODEL', 'llama3.2')}")
    print()

    # Тестовые запросы
    test_queries = [
        "посчитай два плюс два",
        "дай определение искусственного интеллекта в одном предложении",
        "скажи короткий анекдот"
    ]

    for i, query in enumerate(test_queries, 1):
        print(f"\n📌 Тест {i}/{len(test_queries)}")
        print("-" * 60)
        test_text_to_llm(query)
        print()


if __name__ == "__main__":
    main()

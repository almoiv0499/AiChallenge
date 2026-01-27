import speech_recognition as sr
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

class VoiceAgent:
    def __init__(self, ollama_url=None, model=None):
        """Инициализация голосового агента с Ollama"""
        self.recognizer = sr.Recognizer()
        self.ollama_url = ollama_url or os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434/api')
        self.model = model or os.getenv('OLLAMA_MODEL', 'llama3.2')

        # Проверяем доступность Ollama
        if not self._check_ollama_available():
            raise ConnectionError(
                f"Ollama недоступна по адресу {self.ollama_url}\n"
                "Убедитесь, что Ollama запущена: ollama serve"
            )

    def _check_ollama_available(self):
        """Проверка доступности Ollama API"""
        try:
            response = requests.get(f"{self.ollama_url.replace('/api', '')}/api/tags", timeout=5)
            return response.status_code == 200
        except Exception:
            return False

    def listen(self):
        """Прослушивание и распознавание речи с микрофона"""
        print("\n🎤 Говорите...")

        with sr.Microphone() as source:
            # Настройка для снижения шума
            self.recognizer.adjust_for_ambient_noise(source, duration=0.5)

            try:
                # Записываем аудио
                audio = self.recognizer.listen(source, timeout=5, phrase_time_limit=10)
                print("🔄 Обрабатываю речь...")

                # Распознаем речь (используем Google Speech Recognition API)
                text = self.recognizer.recognize_google(audio, language='ru-RU')
                print(f"📝 Распознано: {text}")
                return text

            except sr.WaitTimeoutError:
                print("❌ Тайм-аут: не услышал команду")
                return None
            except sr.UnknownValueError:
                print("❌ Не удалось распознать речь")
                return None
            except sr.RequestError as e:
                print(f"❌ Ошибка сервиса распознавания: {e}")
                return None

    def get_llm_response(self, text):
        """Отправка текста в Ollama и получение ответа"""
        try:
            print(f"🤖 Отправляю запрос в Ollama (модель: {self.model})...")

            # Формируем запрос для Ollama Chat API
            payload = {
                "model": self.model,
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
                f"{self.ollama_url}/chat",
                json=payload,
                timeout=60
            )

            if response.status_code != 200:
                print(f"❌ Ошибка Ollama API: {response.status_code}")
                return None

            # Извлекаем ответ
            result = response.json()
            response_text = result.get("message", {}).get("content", "Нет ответа")
            return response_text

        except requests.exceptions.Timeout:
            print("❌ Тайм-аут при обращении к Ollama")
            return None
        except Exception as e:
            print(f"❌ Ошибка при обращении к LLM: {e}")
            return None

    def process_voice_command(self):
        """Основной цикл: слушаем → распознаем → отправляем в LLM → выводим ответ"""
        # Слушаем голосовую команду
        text = self.listen()

        if text is None:
            return None

        # Отправляем в LLM и получаем ответ
        response = self.get_llm_response(text)

        if response:
            print(f"\n✅ Ответ LLM:\n{response}\n")
            return response

        return None


def main():
    """Основная функция для тестирования агента"""
    print("=" * 60)
    print("🎙️  ГОЛОСОВОЙ АГЕНТ (Speech → Ollama → Text)")
    print("=" * 60)
    print("\n📋 Конфигурация:")
    print(f"  Ollama URL: {os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434/api')}")
    print(f"  Модель: {os.getenv('OLLAMA_MODEL', 'llama3.2')}")
    print("\n📝 Инструкция:")
    print("1. Скажите команду в микрофон")
    print("2. Агент распознает речь и отправит в Ollama")
    print("3. Получите текстовый ответ")
    print("\n💡 Примеры команд:")
    print("  - 'посчитай два плюс два'")
    print("  - 'дай определение искусственного интеллекта'")
    print("  - 'скажи анекдот'")
    print("\nДля выхода скажите 'выход' или нажмите Ctrl+C")
    print("=" * 60)

    try:
        agent = VoiceAgent()

        while True:
            result = agent.process_voice_command()

            # Проверяем, не хочет ли пользователь выйти
            if result and any(word in result.lower() for word in ['выход', 'exit', 'quit', 'стоп']):
                print("\n👋 До свидания!")
                break

            input("\n⏎ Нажмите Enter для следующей команды или Ctrl+C для выхода...\n")

    except KeyboardInterrupt:
        print("\n\n👋 Работа завершена!")
    except Exception as e:
        print(f"\n❌ Произошла ошибка: {e}")


if __name__ == "__main__":
    main()

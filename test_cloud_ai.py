import sys
import os
import time

# Ensure backend/lumir is in path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend", "lumir"))

from cloud_providers import call_gemini, call_sarvam, get_api_key

def test_apis():
    print("=" * 60)
    print("🧪 INTRA CLOUD AI PROVIDER TEST BENCH")
    print("=" * 60)

    gemini_key = get_api_key("gemini")
    sarvam_key = get_api_key("sarvam")

    if not gemini_key and not sarvam_key:
        print("\n⚠️ No API keys found in .env or Database!")
        print("Please open the .env file in the root folder and add your keys:")
        print("  GEMINI_API_KEY=AIzaSy...")
        print("  SARVAM_API_KEY=...")
        print("=" * 60)
        return

    # 1. TEST GOOGLE GEMINI
    if gemini_key:
        masked = gemini_key[:8] + "..." + gemini_key[-4:]
        print(f"\n[1] 🌟 Testing Google Gemini Flash (Key: {masked})...")
        start = time.time()
        try:
            reply = call_gemini(
                prompt="Namaste Lumir! Ek chote line me batao aap kaun ho?",
                system_prompt="You are Lumir, a witty, warm Indian female AI companion.",
                model="gemini-flash-lite-latest"
            )
            elapsed = time.time() - start
            print(f"✅ Gemini Response (gemini-flash-lite-latest) in {elapsed:.2f}s:")
            print(f"   \"{reply}\"")
        except Exception as e:
            print(f"❌ Gemini Error: {e}")
    else:
        print("\n[1] ⚪ Google Gemini: Key not set (skipping).")
        print("\n[1] ⚪ Google Gemini: Key not set (skipping).")

    # 2. TEST SARVAM AI
    if sarvam_key:
        masked = sarvam_key[:6] + "..." + sarvam_key[-4:]
        print(f"\n[2] 🇮🇳 Testing Sarvam AI (Key: {masked})...")
        start = time.time()
        try:
            reply = call_sarvam(
                prompt="Namaste! Ek pyare se sentence me apna intro do.",
                system_prompt="Aap Lumir ho, ek caring aur pyari Indian companion.",
                model="sarvam-105b-conversations"
            )
            elapsed = time.time() - start
            print(f"✅ Sarvam Response (sarvam-105b-conversations) in {elapsed:.2f}s:")
            print(f"   \"{reply}\"")
        except Exception as e:
            print(f"❌ Sarvam Error: {e}")
    else:
        print("\n[2] ⚪ Sarvam AI: Key not set (skipping).")

    print("\n" + "=" * 60)
    print("🎉 Test Finished!")
    print("=" * 60)

if __name__ == "__main__":
    test_apis()

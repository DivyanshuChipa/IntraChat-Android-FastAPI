from unittest.mock import patch, MagicMock
from backend.lumir.ai_engine import ask_ai, detect_ai_provider, build_llamacpp_messages
from backend.users import get_ai_config, set_ai_config


def test_detect_ai_provider():
    assert detect_ai_provider("http://localhost:11434", "auto") == "ollama"
    assert detect_ai_provider("http://192.168.31.94:8080", "auto") == "llamacpp"
    assert detect_ai_provider("http://example.com/v1", "auto") == "llamacpp"
    assert detect_ai_provider("http://localhost:11434", "llamacpp") == "llamacpp"
    assert detect_ai_provider("http://192.168.31.94:8080", "ollama") == "ollama"


def test_build_llamacpp_messages_text_and_vision():
    messages = [
        {"role": "system", "content": "You are Lumir."},
        {"role": "user", "content": "Look at this", "images": ["fakebase64"]}
    ]
    formatted = build_llamacpp_messages(messages)
    assert len(formatted) == 2
    assert formatted[0] == {"role": "system", "content": "You are Lumir."}
    assert formatted[1]["role"] == "user"
    assert isinstance(formatted[1]["content"], list)
    assert formatted[1]["content"][0] == {"type": "text", "text": "Look at this"}
    assert formatted[1]["content"][1]["type"] == "image_url"
    assert formatted[1]["content"][1]["image_url"]["url"] == "data:image/jpeg;base64,fakebase64"


def test_get_and_set_ai_config_provider():
    cfg = get_ai_config()
    assert "ai_provider" in cfg
    assert cfg["ai_provider"] == "auto"

    set_ai_config(
        enabled=True,
        model="qwen2.5:0.5b",
        url="http://192.168.31.94:8080",
        vision_model="gemma:27b-cloud",
        fallback="gemma3:270m",
        smart_models="qwen2.5:0.5b",
        voice_node_url="http://localhost:8005",
        voice_node_enabled=False,
        ai_provider="llamacpp"
    )

    updated_cfg = get_ai_config()
    assert updated_cfg["ai_enabled"] is True
    assert updated_cfg["ai_provider"] == "llamacpp"
    assert updated_cfg["ollama_url"] == "http://192.168.31.94:8080"


@patch("requests.post")
def test_ask_ai_ollama_flow(mock_post):
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {
        "message": {"content": "Hello from Ollama!"}
    }
    mock_post.return_value = mock_resp

    config = {
        "ai_enabled": True,
        "ollama_url": "http://localhost:11434",
        "ai_provider": "ollama",
        "ai_model": "llama3.2:3b",
        "ai_fallback": "gemma3:270m"
    }

    reply = ask_ai("Namaste", config=config, sender="Divya")
    assert reply == "Hello from Ollama!"
    assert mock_post.called
    endpoint_called = mock_post.call_args[0][0]
    assert endpoint_called == "http://localhost:11434/api/chat"


@patch("requests.post")
def test_ask_ai_llamacpp_flow(mock_post):
    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {
        "choices": [
            {"message": {"content": "Hello from llama.cpp!"}}
        ]
    }
    mock_post.return_value = mock_resp

    config = {
        "ai_enabled": True,
        "ollama_url": "http://192.168.31.94:8080",
        "ai_provider": "auto",  # Port 8080 should auto-detect llamacpp
        "ai_model": "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "ai_fallback": "gemma3:270m"
    }

    reply = ask_ai("Kaise ho?", config=config, sender="Divya")
    assert reply == "Hello from llama.cpp!"
    assert mock_post.called
    endpoint_called = mock_post.call_args[0][0]
    assert endpoint_called == "http://192.168.31.94:8080/v1/chat/completions"


@patch("requests.post")
def test_ask_ai_llamacpp_fallback_flow(mock_post):
    # First call (main model) fails, second call (fallback model) succeeds
    fail_resp = MagicMock()
    fail_resp.status_code = 500
    fail_resp.text = "Internal Server Error"

    success_resp = MagicMock()
    success_resp.status_code = 200
    success_resp.json.return_value = {
        "choices": [
            {"message": {"content": "Fallback answer"}}
        ]
    }

    mock_post.side_effect = [fail_resp, success_resp]

    config = {
        "ai_enabled": True,
        "ollama_url": "http://192.168.31.94:8080",
        "ai_provider": "llamacpp",
        "ai_model": "non-existent-model",
        "ai_fallback": "qwen2.5:0.5b"
    }

    reply = ask_ai("Hello", config=config, sender="Divya")
    assert "Fallback answer" in reply
    assert "(Fallback Mode)" in reply
    assert mock_post.call_count == 2

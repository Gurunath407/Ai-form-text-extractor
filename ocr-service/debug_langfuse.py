import os
from langfuse import Langfuse
import inspect

print(f"Langfuse package version: {inspect.getmodule(Langfuse).__version__ if hasattr(inspect.getmodule(Langfuse), '__version__') else 'unknown'}")

try:
    lf = Langfuse(
        public_key="pk-lf-demo",
        secret_key="sk-lf-demo",
        host="http://localhost:3000"
    )
    print(f"Langfuse object created: {lf}")
    print(f"Attributes: {dir(lf)}")
    
    if hasattr(lf, 'trace'):
        print("trace method EXISTS")
    else:
        print("trace method MISSING")
except Exception as e:
    print(f"Error: {e}")

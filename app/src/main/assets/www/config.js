(function(){
  const nativeBase = window.CifliXNative && window.CifliXNative.getApiBase ? window.CifliXNative.getApiBase() : "";
  window.CIFLIX_API_BASE = (nativeBase && nativeBase.trim()) ? nativeBase.replace(/\/$/, "") : "http://127.0.0.1:8000/api";
})();

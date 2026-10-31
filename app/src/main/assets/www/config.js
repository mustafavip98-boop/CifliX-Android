(function(){
  const base = window.CifliXNative && window.CifliXNative.getApiBase ? window.CifliXNative.getApiBase() : "";
  window.CIFLIX_API_BASE = base ? base.replace(/\\/$/, "") : "/api";
})();

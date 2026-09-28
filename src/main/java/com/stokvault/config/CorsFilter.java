package com.stokvault.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * Allows a frontend served from a different address (e.g. a React dev server on
 * localhost:5173) to call this API from the browser. Browsers block such cross-origin
 * calls unless the API sends these CORS headers.
 * The built-in dashboard at /stokvault/ is served from the same address, so it doesn't need this.
 */
// A ContainerResponseFilter runs on every response just before it is sent
@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        // "*" allows any site. Fine for local development; a real deployment would list its frontend's URL.
        headers.putSingle("Access-Control-Allow-Origin", "*");
        headers.putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.putSingle("Access-Control-Allow-Headers", "Content-Type, Accept");
    }
}

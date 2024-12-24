package com.cabin.express.inter;

import com.cabin.express.http.CabinRequest;
import com.cabin.express.http.CabinResponse;

import java.io.IOException;

@FunctionalInterface
public interface CabinHandler {
    void handle(CabinRequest request, CabinResponse response) throws IOException;
}
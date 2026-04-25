package com.example.myapp.network;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("/update-location")
    Call<ResponseBody> updateLocation(@Body LocationPayload payload);

    @POST("/submit-objective")
    Call<ResponseBody> submitObjective(@Body DayObjective payload);
}

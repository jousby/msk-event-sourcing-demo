package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class CdkPipelineApp {
    public static void main(final String[] args) {
        App app = new App();

        new CdkPipelineStack(app, "CdkPipelineStack");

        app.synth();
    }
}

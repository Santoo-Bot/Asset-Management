package id.perusahaan.asetga.data.seed

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedModule {
    @Binds
    abstract fun bindSeeder(impl: DebugSampleDataSeeder): SampleDataSeeder
}

package id.perusahaan.asetga.data.seed

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SeedModule {
    @Provides
    fun provideSeeder(): SampleDataSeeder = object : SampleDataSeeder {
        override suspend fun seedIfNeeded() = Unit
    }
}

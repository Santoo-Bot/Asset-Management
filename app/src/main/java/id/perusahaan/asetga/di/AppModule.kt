package id.perusahaan.asetga.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData
import id.perusahaan.asetga.data.local.Migrations
import id.perusahaan.asetga.data.repository.AssetRepositoryImpl
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.DashboardRepositoryImpl
import id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl
import id.perusahaan.asetga.data.repository.OperationRepositoryImpl
import id.perusahaan.asetga.data.repository.OpnameRepositoryImpl
import id.perusahaan.asetga.data.repository.StockRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.data.repository.UserRepositoryImpl
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.DashboardRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.OperationRepository
import id.perusahaan.asetga.domain.repository.OpnameRepository
import id.perusahaan.asetga.domain.repository.StockRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.security.PasswordHasher
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(DefaultData.callback)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun userDao(db: AppDatabase) = db.userDao()
    @Provides fun categoryDao(db: AppDatabase) = db.categoryDao()
    @Provides fun locationDao(db: AppDatabase) = db.locationDao()
    @Provides fun divisionDao(db: AppDatabase) = db.divisionDao()
    @Provides fun departmentDao(db: AppDatabase) = db.departmentDao()
    @Provides fun vendorDao(db: AppDatabase) = db.vendorDao()
    @Provides fun unitDao(db: AppDatabase) = db.unitDao()
    @Provides fun settingsDao(db: AppDatabase) = db.settingsDao()
    @Provides fun auditLogDao(db: AppDatabase) = db.auditLogDao()
    @Provides fun assetDao(db: AppDatabase) = db.assetDao()
    @Provides fun mutationDao(db: AppDatabase) = db.mutationDao()
    @Provides fun serviceDao(db: AppDatabase) = db.serviceDao()
    @Provides fun stockDao(db: AppDatabase) = db.stockDao()
    @Provides fun opnameDao(db: AppDatabase) = db.opnameDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun providePasswordHasher(): PasswordHasher = PasswordHasher()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun masterData(impl: MasterDataRepositoryImpl): MasterDataRepository
    @Binds abstract fun users(impl: UserRepositoryImpl): UserRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun auditLog(impl: AuditLogRepositoryImpl): AuditLogRepository
    @Binds abstract fun assets(impl: AssetRepositoryImpl): AssetRepository
    @Binds abstract fun operations(impl: OperationRepositoryImpl): OperationRepository
    @Binds abstract fun stock(impl: StockRepositoryImpl): StockRepository
    @Binds abstract fun dashboard(impl: DashboardRepositoryImpl): DashboardRepository
    @Binds abstract fun opname(impl: OpnameRepositoryImpl): OpnameRepository
}

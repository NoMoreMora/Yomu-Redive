package org.koitharu.kotatsu.alternatives.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide gate that lets long-running background work (currently chapter downloads) step aside while a
 * manga migration is executing, so the two don't fight over CPU and network and overheat the device.
 *
 * Reference-counted, so overlapping migrations — the foreground [org.koitharu.kotatsu.alternatives.ui.MigrationService]
 * and an on-screen "apply" from the migration list — are both accounted for; downloads resume only once the
 * last one finishes. Only migration EXECUTION is counted here, not the (already capped) auto-match phase.
 */
@Singleton
class MigrationCoordinator @Inject constructor() {

	private val activeCount = MutableStateFlow(0)

	/** True while at least one migration is currently running. */
	val isActive: Boolean
		get() = activeCount.value > 0

	fun begin() {
		activeCount.update { it + 1 }
	}

	fun end() {
		activeCount.update { (it - 1).coerceAtLeast(0) }
	}

	/** Suspends until no migration is running; returns immediately when none is. */
	suspend fun awaitIdle() {
		activeCount.first { it <= 0 }
	}

	/** Runs [block] counted as an active migration, always balancing the count even on failure/cancel. */
	suspend fun <T> withMigration(block: suspend () -> T): T {
		begin()
		try {
			return block()
		} finally {
			end()
		}
	}
}

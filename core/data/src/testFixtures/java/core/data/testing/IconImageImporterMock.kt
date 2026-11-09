package core.data.testing

import core.data.icons.IIconImageImporter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

/** A MockK [IIconImageImporter] returning [fileName] for every import. */
fun createIconImageImporterMock(fileName: String? = null): IIconImageImporter {
    val mockIconImageImporter: IIconImageImporter = mockk()
    coEvery { mockIconImageImporter.importImage(any()) } returns fileName
    every { mockIconImageImporter.deleteImage(any()) } just Runs
    return mockIconImageImporter
}

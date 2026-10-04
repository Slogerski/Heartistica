$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Convert-ToAbsorptionHeart {
    param(
        [Parameter(Mandatory)] [string] $Source,
        [Parameter(Mandatory)] [string] $Target,
        [switch] $KeepNeutralPixels
    )

    $input = [System.Drawing.Bitmap]::new($Source)
    $output = [System.Drawing.Bitmap]::new($input.Width, $input.Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    try {
        for ($y = 0; $y -lt $input.Height; $y++) {
            for ($x = 0; $x -lt $input.Width; $x++) {
                $pixel = $input.GetPixel($x, $y)
                if ($pixel.A -eq 0) {
                    $output.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                    continue
                }

                $isColoredHeart = $pixel.R -gt ($pixel.G + 20) -and $pixel.R -gt ($pixel.B + 20)
                if ($KeepNeutralPixels -and -not $isColoredHeart) {
                    $output.SetPixel($x, $y, $pixel)
                    continue
                }

                $intensity = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B)) / 255.0
                $red = [int](105 + 150 * $intensity)
                $green = [int](55 + 165 * $intensity)
                $blue = [int](5 + 55 * $intensity)
                $output.SetPixel($x, $y,
                    [System.Drawing.Color]::FromArgb($pixel.A, $red, $green, $blue))
            }
        }
        $output.Save($Target, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $input.Dispose()
        $output.Dispose()
    }

    Write-Output "Generated $Target from $Source."
}

$textureDirectory = Join-Path $PSScriptRoot '..\common\src\main\resources\assets\heartistica\textures\gui'
Convert-ToAbsorptionHeart `
    -Source (Join-Path $textureDirectory 'heart.png') `
    -Target (Join-Path $textureDirectory 'heart_absorption.png')
Convert-ToAbsorptionHeart `
    -Source (Join-Path $textureDirectory 'heart_half.png') `
    -Target (Join-Path $textureDirectory 'heart_absorption_half.png') `
    -KeepNeutralPixels

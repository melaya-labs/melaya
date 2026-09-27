using System.Runtime.CompilerServices;

// Lets the unit test project construct internal types (MelayaHttpClient, the *Api
// classes) directly with a mock HttpMessageHandler, instead of hitting the network.
// The public surface (MelayaClient and friends) is unaffected.
[assembly: InternalsVisibleTo("Melaya.Tests")]

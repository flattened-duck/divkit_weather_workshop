enum DocumentLoaderError: Error {
    case badStatus(Int)
    case malformed(String)
}
